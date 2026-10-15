package vn.covie.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.support.PostgresIntegrationTest;

/** Real PostgreSQL/Flyway checks: discounts must never escape into buyer payments. */
class BillingVoucherIntegrationTest extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired BillingVoucherService vouchers;

    private record Fixture(UUID merchant, String code) {}

    private Fixture fixture(int limitPerMerchant, int totalLimit) {
        UUID user = UUID.randomUUID();
        UUID merchant = UUID.randomUUID();
        String code = "PROMO_" + UUID.randomUUID().toString().substring(0,8).toUpperCase();
        Instant now=Instant.now();
        jdbc.update("""
            insert into users(id,display_name,account_status,created_at,updated_at)
            values (?,'Billing tester','ACTIVE',?,?)
            """,user,Timestamp.from(now),Timestamp.from(now));
        jdbc.update("""
            insert into merchant_profiles
              (id,name,slug,merchant_status,moderation_status,created_at,updated_at,version)
            values (?,'Voucher merchant',?,'ACTIVE','APPROVED',?,?,0)
            """,merchant,"voucher-"+merchant,Timestamp.from(now),Timestamp.from(now));
        jdbc.update("""
            insert into billing_vouchers
              (code,plan_code,discount_type,discount_value,max_redemptions,per_merchant_limit,
               starts_at,ends_at,enabled,created_by_user_id,created_at)
            values (?,'SELLER_STANDARD','FIXED',1000,?,?,?, ?,true,?,?)
            """,code,totalLimit,limitPerMerchant,Timestamp.from(now.minusSeconds(120)),
                Timestamp.from(now.plusSeconds(86400)),user,Timestamp.from(now));
        return new Fixture(merchant,code);
    }

    private void invoice(Fixture f, UUID invoiceId, BillingVoucherService.Discount discount) {
        UUID plan=jdbc.queryForObject("""
            select id from billing_plan_versions where plan_code='SELLER_STANDARD'
            order by revision limit 1
            """,UUID.class);
        UUID policy=jdbc.queryForObject("""
            select id from billing_policy_versions order by revision limit 1
            """,UUID.class);
        Instant now=Instant.now();
        jdbc.update("""
            insert into billing_invoices
              (id,invoice_number,merchant_id,plan_version_id,policy_version_id,status,
               amount_vnd,period_start,period_end,pricing_snapshot,idempotency_key,
               request_fingerprint,created_at,expires_at,grace_until)
            values (?,?,?,?,?,'PENDING_PAYMENT',?,?,?,'{}'::jsonb,?,?,?,?,?)
            """,invoiceId,"CV-"+invoiceId.toString().substring(0,12),f.merchant(),plan,policy,
            discount.amountDueVnd(),Timestamp.from(now),Timestamp.from(now.plusSeconds(86400*30)),
            "voucher-"+invoiceId,"test-only",Timestamp.from(now),
            Timestamp.from(now.plusSeconds(900)),Timestamp.from(now.plusSeconds(86400*33)));
    }

    @Test @Transactional
    void recordsImmutableDiscountSnapshotWithoutChangingSellerPlanPrice() {
        Fixture f=fixture(1,5);
        UUID invoiceId=UUID.randomUUID();
        var d=vouchers.reserve(f.code(),"SELLER_STANDARD",f.merchant(),10000);
        assertThat(d.discountVnd()).isEqualTo(1000L);
        assertThat(d.amountDueVnd()).isEqualTo(9000L);
        invoice(f,invoiceId,d);
        vouchers.attachReservation(invoiceId,f.merchant(),10000,d);
        assertThat(jdbc.queryForObject("select amount_vnd from billing_invoices where id=?",
            Long.class,invoiceId)).isEqualTo(9000L);
        assertThat(jdbc.queryForObject("select discount_vnd from billing_voucher_redemptions where invoice_id=?",
            Long.class,invoiceId)).isEqualTo(1000L);
        assertThat(jdbc.queryForObject("select price_vnd from billing_plan_versions where plan_code='SELLER_STANDARD'",
            Long.class)).isEqualTo(10000L);
    }

    @Test @Transactional
    void pendingInvoicesHoldQuotaAndStopSameMerchantRedeemingTwice() {
        Fixture f=fixture(1,1);
        UUID invoiceId=UUID.randomUUID();
        var d=vouchers.reserve(f.code(),"SELLER_STANDARD",f.merchant(),10000);
        invoice(f,invoiceId,d);
        vouchers.attachReservation(invoiceId,f.merchant(),10000,d);
        assertThrows(RuntimeException.class, ()->
            vouchers.reserve(f.code(),"SELLER_STANDARD",f.merchant(),10000));
    }

    @Test @Transactional
    void signedProviderFinalityCanReleaseQuotaWhenInvoiceVoided() {
        Fixture f=fixture(1,1);
        UUID invoiceId=UUID.randomUUID();
        var d=vouchers.reserve(f.code(),"SELLER_STANDARD",f.merchant(),10000);
        invoice(f,invoiceId,d);
        vouchers.attachReservation(invoiceId,f.merchant(),10000,d);
        // Production only VOID after authenticated payOS final state with zero received amount.
        jdbc.update("update billing_invoices set status='VOID' where id=?",invoiceId);
        var next=vouchers.reserve(f.code(),"SELLER_STANDARD",f.merchant(),10000);
        assertThat(next.amountDueVnd()).isEqualTo(9000L);
    }
}
