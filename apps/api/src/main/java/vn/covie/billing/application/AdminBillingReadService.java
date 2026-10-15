package vn.covie.billing.application;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.shared.time.AppClock;

/** Financial data is server-owned, admin-only, and returned without PSP secrets. */
@Service
public class AdminBillingReadService {
    private final JdbcTemplate jdbc;
    private final AdminAccessPolicy admins;
    private final AppClock clock;

    public AdminBillingReadService(JdbcTemplate jdbc, AdminAccessPolicy admins, AppClock clock) {
        this.jdbc=jdbc; this.admins=admins; this.clock=clock;
    }

    @Transactional(readOnly=true)
    public Overview overview() {
        admins.requireAdmin();
        return jdbc.query("""
            select
              (select coalesce(sum(amount_vnd),0) from billing_invoices
               where status='PAID') as collected_vnd,
              (select count(*) from billing_invoices
               where status='PAID') as paid_invoices,
              (select count(*) from billing_invoices
               where status='PENDING_PAYMENT') as pending_invoices,
              (select count(distinct merchant_id) from billing_entitlement_grants
               where revoked_at is null and valid_from <= ? and valid_until > ?) as entitled_shops,
              (select count(*) from billing_reconciliation_observations
               where discrepancy <> 'NONE') as reconcile_alerts
            """, rs -> {
            rs.next();
            return new Overview(rs.getLong("collected_vnd"),rs.getLong("paid_invoices"),
                rs.getLong("pending_invoices"), rs.getLong("entitled_shops"),
                rs.getLong("reconcile_alerts"));
        }, Timestamp.from(clock.now()),Timestamp.from(clock.now()));
    }

    @Transactional(readOnly=true)
    public List<Invoice> invoices(UUID merchantId, int limit, int offset) {
        admins.requireAdmin();
        if(limit < 1 || limit > 100 || offset < 0 || offset > 500_000)
            throw new IllegalArgumentException("Invalid billing page");
        if(merchantId==null){
            return jdbc.query("""
                select id,merchant_id,invoice_number,status,amount_vnd,created_at,paid_at
                from billing_invoices order by created_at desc,id desc limit ? offset ?
                """, (rs,n)->row(rs),limit,offset);
        }
        return jdbc.query("""
            select id,merchant_id,invoice_number,status,amount_vnd,created_at,paid_at
            from billing_invoices where merchant_id=?
            order by created_at desc,id desc limit ? offset ?
            """, (rs,n)->row(rs),merchantId,limit,offset);
    }

    private static Invoice row(java.sql.ResultSet rs) throws java.sql.SQLException {
        Timestamp paid = rs.getTimestamp("paid_at");
        return new Invoice((UUID)rs.getObject("id"),(UUID)rs.getObject("merchant_id"),
            rs.getString("invoice_number"),rs.getString("status"),rs.getLong("amount_vnd"),
            rs.getTimestamp("created_at").toInstant(),paid==null?null:paid.toInstant());
    }

    public record Overview(long collectedVnd,long paidInvoices,long pendingInvoices,
                           long entitledShops,long reconciliationAlerts) {}
    public record Invoice(UUID id,UUID merchantId,String invoiceNumber,String status,
                          long amountVnd,Instant createdAt,Instant paidAt) {}
}
