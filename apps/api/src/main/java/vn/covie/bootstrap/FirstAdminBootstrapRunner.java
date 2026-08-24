package vn.covie.bootstrap;

import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FirstAdminBootstrapRunner implements ApplicationRunner {
    private final Environment environment;
    private final JdbcTemplate jdbc;

    public FirstAdminBootstrapRunner(Environment environment, JdbcTemplate jdbc) {
        this.environment = environment;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String configured = PilotBootstrapProperties.from(environment).firstAdminEmail();
        if (configured == null || configured.isBlank()) return;

        requirePrerequisiteV013();
        String email = normalizeEmail(configured);
        List<UUID> users = jdbc.query(
                """
                select distinct u.id
                  from users u
                  join user_identities g
                    on g.user_id = u.id
                   and g.provider = 'GOOGLE'
                   and g.email_normalized = ?
                   and g.verified_at is not null
                  join user_identities e
                    on e.user_id = u.id
                   and e.provider = 'EMAIL'
                   and e.email_normalized = ?
                 where u.account_status = 'ACTIVE'
                   and u.deleted_at is null
                """,
                (rs, rowNum) -> rs.getObject(1, UUID.class),
                email,
                email);

        if (users.size() != 1) {
            throw new IllegalStateException(
                    "COVIE_BOOTSTRAP_FIRST_ADMIN_EMAIL must resolve to exactly one active user with verified Google and EMAIL onboarding");
        }

        UUID userId = users.getFirst();
        int inserted = jdbc.update(
                """
                insert into system_role_assignments(id, user_id, role, created_at)
                values (uuidv7(), ?, 'ADMIN', now())
                on conflict (user_id, role) do nothing
                """, userId);
        if (inserted == 1) {
            jdbc.update(
                    """
                    insert into audit_logs(
                        id, actor_user_id, actor_type, event_category, action,
                        entity_type, entity_id, before_snapshot, after_snapshot,
                        request_id, ip_hash_or_metadata, created_at)
                    values (
                        uuidv7(), null, 'SYSTEM', 'SECURITY', 'FIRST_ADMIN_GRANTED',
                        'USER', ?, null, jsonb_build_object('source','BOOTSTRAP'),
                        null, null, now())
                    """, userId);
        }
    }

    private void requirePrerequisiteV013() {
        Integer version = jdbc.queryForObject(
                """
                select coalesce(max(nullif(regexp_replace(version, '[^0-9].*$', ''), '')::int), 0)
                  from flyway_schema_history where success
                """, Integer.class);
        if (version == null || version < 13) {
            throw new IllegalStateException("First-admin bootstrap requires prerequisite Flyway V013");
        }
    }

    static String normalizeEmail(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
