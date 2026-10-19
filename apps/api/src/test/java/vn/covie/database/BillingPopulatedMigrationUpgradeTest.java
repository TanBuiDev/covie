package vn.covie.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class BillingPopulatedMigrationUpgradeTest {
    @ParameterizedTest
    @ValueSource(strings = {"019", "030"})
    void preservesPopulatedRowsAndEnforcesConfiguredReminderBounds(String version) {
        try (var postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgis/postgis:18-3.6")
                .asCompatibleSubstituteFor("postgres")).withDatabaseName("billing_upgrade")
                .withUsername("test").withPassword("test-only")) {
            postgres.start();
            var source = new DriverManagerDataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
            Flyway.configure().dataSource(source).locations("classpath:db/migration").target(version).load().migrate();
            var jdbc = new JdbcTemplate(source);
            UUID user = UUID.randomUUID(), merchant = UUID.randomUUID(), category = UUID.randomUUID(), item = UUID.randomUUID();
            jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at) values (?,'Migration owner','ACTIVE',now(),now())", user);
            jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version) values (?,'Existing shop',?,'ACTIVE','APPROVED',now(),now(),0)", merchant, "billing-upgrade-" + merchant);
            jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version) values (?,?,?,'OWNER','ACTIVE',now(),now(),0)", UUID.randomUUID(), merchant, user);
            jdbc.update("insert into categories(id,name,slug,catalog_type,status) values (?,'Existing category',?,'PRODUCT','ACTIVE')", category, "upgrade-" + category);
            jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,description,listing_status,moderation_status,created_at,updated_at) values (?,?,'PRODUCT','STANDARD',?,'Existing item',?,'Description stays separate','ACTIVE','APPROVED',now(),now())", item, merchant, category, "upgrade-" + item);
            for (String kind : new String[]{"LIKE", "SAVE"}) {
                jdbc.update("insert into catalog_social_reactions(catalog_item_id,user_id,kind) values (?,?,?)", item, user, kind);
            }
            for (String status : new String[]{"ACTIVE", "HIDDEN", "DELETED"}) {
                jdbc.update("insert into catalog_social_comments(id,catalog_item_id,author_user_id,body,status) values (?,?,?,'Existing Unicode bình luận',?)", UUID.randomUUID(), item, user, status);
            }
            if (version.equals("030")) seedFinancialHistory(jdbc, user, merchant);
            Map<String, String> before = snapshot(jdbc);
            var latest = Flyway.configure().dataSource(source).locations("classpath:db/migration").load();
            assertThat(latest.migrate().migrationsExecuted).isPositive();
            latest.validate();
            assertThat(snapshot(jdbc)).containsAllEntriesOf(before);
            if (version.equals("019")) {
                for (String table : new String[]{"billing_invoices", "billing_payment_attempts", "billing_entitlement_grants", "merchant_subscriptions", "billing_trial_claims"}) {
                    assertThat(jdbc.queryForObject("select count(*) from " + table, Long.class)).as(table).isZero();
                }
            }
            assertThat(latest.migrate().migrationsExecuted).isZero();
            assertThat(snapshot(jdbc)).containsAllEntriesOf(before);
            for (int valid : new int[]{1, 2, 60}) insertReminder(jdbc, merchant, valid);
            for (int invalid : new int[]{0, 61, -1}) {
                assertThatThrownBy(() -> insertReminder(jdbc, merchant, invalid))
                    .as("reminder offset %s must be rejected by PostgreSQL", invalid)
                    .isInstanceOf(DataIntegrityViolationException.class);
            }
            assertThat(snapshot(jdbc).get("catalog_social_comments")).isEqualTo(before.get("catalog_social_comments"));
        }
    }

    private void insertReminder(JdbcTemplate jdbc, UUID merchant, int days) {
        jdbc.update("insert into billing_reminder_dispatches(merchant_id,period_end,days_before,created_at) values (?, '2027-01-31T00:00:00Z', ?, now())", merchant, days);
    }

    private void seedFinancialHistory(JdbcTemplate jdbc, UUID user, UUID merchant) {
        UUID invoice = UUID.randomUUID();
        jdbc.update("""
            insert into billing_invoices(id,invoice_number,merchant_id,plan_version_id,policy_version_id,status,
                amount_vnd,period_start,period_end,pricing_snapshot,idempotency_key,request_fingerprint,
                created_at,expires_at,paid_at,payos_order_code,grace_until)
            select ?, 'UPGRADE-PAID', ?, v.id, p.id, 'PAID', 10000,
                '2026-01-31T00:00:00Z','2026-02-28T00:00:00Z','{"priceVnd":10000}'::jsonb,
                'migration-key',repeat('a',64),'2026-01-30T00:00:00Z','2026-01-30T00:15:00Z',
                '2026-01-30T00:01:00Z',4000000000,'2026-03-03T00:00:00Z'
            from billing_plan_versions v cross join billing_policy_versions p
            where v.plan_code='SELLER_STANDARD' and v.revision=1 and p.revision=1
            """, invoice, merchant);
        jdbc.update("""
            insert into billing_payment_attempts(id,invoice_id,provider,provider_order_id,provider_transaction_id,
                status,expected_amount_vnd,received_amount_vnd,created_at,settled_at,provider_payment_link_id,checkout_url,qr_payload)
            values (?,?,'PAYOS','4000000000','fixture-reference','SUCCEEDED',10000,10000,
                '2026-01-30T00:00:00Z','2026-01-30T00:01:00Z','migration-link',
                'https://pay.payos.vn/web/migration-link','migration-qr')
            """, UUID.randomUUID(), invoice);
        jdbc.update("""
            insert into merchant_subscriptions(merchant_id,plan_code,status,period_start,period_end,grace_until,created_at,updated_at)
            values (?,'SELLER_STANDARD','ACTIVE','2026-01-31T00:00:00Z','2026-02-28T00:00:00Z','2026-03-03T00:00:00Z',now(),now())
            """, merchant);
        jdbc.update("""
            insert into billing_entitlement_grants(id,merchant_id,source_type,source_id,valid_from,valid_until,created_at)
            values (?,?,'PAID_INVOICE',?,'2026-01-31T00:00:00Z','2026-02-28T00:00:00Z',now())
            """, UUID.randomUUID(), merchant, invoice);
        jdbc.update("insert into billing_finance_cases(id,invoice_id,merchant_id,kind,status,reason,created_by_user_id,created_at) values (?,?,?,'REFUND','REQUESTED','Fixture support request',?,now())", UUID.randomUUID(), invoice, merchant, user);
        jdbc.update("insert into billing_webhook_inbox(id,provider,provider_event_id,payload_sha256,verified,processing_status,received_at,processed_at,attempt_count) values (?,'PAYOS','fixture-event',repeat('b',64),true,'DONE',now(),now(),1)", UUID.randomUUID());
        jdbc.update("insert into billing_reminder_dispatches(merchant_id,period_end,days_before,created_at) values (?, '2026-02-28T00:00:00Z',7,now())", merchant);
    }

    private Map<String, String> snapshot(JdbcTemplate jdbc) {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String table : jdbc.queryForList("select tablename from pg_tables where schemaname='public' and tablename not in ('flyway_schema_history','spatial_ref_sys') order by tablename", String.class)) {
            rows.put(table, jdbc.queryForObject("select coalesce(jsonb_agg(row_data order by row_data::text),'[]'::jsonb)::text from (select to_jsonb(existing) row_data from \"" + table + "\" existing) all_rows", String.class));
        }
        return rows;
    }
}
