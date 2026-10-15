package vn.covie.billing.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.shared.web.RequestIdAccessor;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

/**
 * Published pricing and policy versions are append-only.
 * Do not retrofit historical invoices when policy/price changes.
 */
@Service
public class BillingCatalogService {
    private final JdbcTemplate jdbc;
    private final AppClock clock;
    private final AdminAccessPolicy admins;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;
    private final OutboxEventWriter outbox;

    public BillingCatalogService(JdbcTemplate jdbc, AppClock clock, AdminAccessPolicy admins,
                                 AuditLogWriter audit, RequestIdAccessor requestIds, OutboxEventWriter outbox) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.admins = admins;
        this.audit = audit;
        this.requestIds = requestIds;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public List<PlanOffer> availablePlans() {
        return jdbc.query("""
            select v.id, v.plan_code, p.display_name, v.revision, v.price_vnd,
                   v.cycle_months, v.effective_at
            from billing_plan_versions v join billing_plans p on p.code = v.plan_code
            where p.retired_at is null and v.effective_at <= ?
              and not exists (
                  select 1 from billing_plan_versions later
                  where later.plan_code = v.plan_code and later.effective_at <= ?
                    and later.revision > v.revision
              )
            order by p.code
            """, (rs, i) -> new PlanOffer(
                (UUID) rs.getObject("id"), rs.getString("plan_code"), rs.getString("display_name"),
                rs.getInt("revision"), rs.getLong("price_vnd"), rs.getInt("cycle_months"),
                rs.getTimestamp("effective_at").toInstant()
            ), java.sql.Timestamp.from(clock.now()), java.sql.Timestamp.from(clock.now()));
    }

    @Transactional(readOnly = true)
    public Policy currentPolicy() {
        return jdbc.query("""
            select id, revision, onboarding_mode, trial_days, grace_days, effective_at
            from billing_policy_versions where effective_at <= ?
            order by effective_at desc limit 1
            """, rs -> {
                if (!rs.next()) throw new IllegalStateException("No effective billing policy.");
                return new Policy((UUID) rs.getObject("id"), rs.getInt("revision"),
                    rs.getString("onboarding_mode"), rs.getInt("trial_days"), rs.getInt("grace_days"),
                    rs.getTimestamp("effective_at").toInstant());
            }, java.sql.Timestamp.from(clock.now()));
    }

    @Transactional
    public PlanOffer publishPrice(String planCode, PriceChange request) {
        var actor = admins.requireAdmin();
        if (request == null || planCode == null || !planCode.matches("[A-Z0-9_]{1,60}")
                || request.priceVnd() < 0 || request.priceVnd() > 1_000_000_000L
                || request.cycleMonths() < 1 || request.cycleMonths() > 120
                || request.effectiveAt() == null || !request.effectiveAt().isAfter(clock.now())) {
            throw badRequest("Invalid price or effective date.");
        }
        List<String> locked = jdbc.query("""
            select code from billing_plans where code = ? and retired_at is null for update
            """, (rs, i) -> rs.getString(1), planCode);
        if (locked.isEmpty()) throw notFound();
        java.sql.Timestamp lastEffective = jdbc.queryForObject("""
            select max(effective_at) from billing_plan_versions where plan_code = ?
            """, java.sql.Timestamp.class, planCode);
        if (lastEffective != null && !request.effectiveAt().isAfter(lastEffective.toInstant())) {
            throw badRequest("A new price must become effective after already published versions.");
        }
        Integer rev = jdbc.queryForObject(
            "select coalesce(max(revision), 0) from billing_plan_versions where plan_code = ?",
            Integer.class, planCode);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into billing_plan_versions
            (id, plan_code, revision, price_vnd, cycle_months, effective_at, published_at, created_by_user_id)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, planCode, rev + 1, request.priceVnd(), request.cycleMonths(),
            java.sql.Timestamp.from(request.effectiveAt()), java.sql.Timestamp.from(clock.now()),
            actor.userId());
        audit.securityEvent(actor.userId(), "BILLING_PRICE_PUBLISHED", "BILLING_PLAN_VERSION", id,
            requestIds.current(), Map.of("planCode", planCode, "revision", rev + 1));
        outbox.append(new OutboxEventIntent("BILLING_PRICE_PUBLISHED", "BILLING_PLAN_VERSION", id,
            Map.of("planCode", planCode, "revision", rev + 1)));
        String displayName = jdbc.queryForObject(
            "select display_name from billing_plans where code = ?", String.class, planCode);
        return new PlanOffer(id, planCode, displayName, rev + 1,
            request.priceVnd(), request.cycleMonths(), request.effectiveAt());
    }

    @Transactional
    public Policy publishPolicy(PolicyChange request) {
        var actor = admins.requireAdmin();
        if (request == null || request.onboardingMode() == null
                || !List.of("PAY_BEFORE_SELL", "TRIAL").contains(request.onboardingMode())
                || request.trialDays() < 0 || request.trialDays() > 365
                || request.graceDays() < 0 || request.graceDays() > 60
                || request.effectiveAt() == null || !request.effectiveAt().isAfter(clock.now())) {
            throw badRequest("Invalid billing policy.");
        }
        // Serialize concurrent policy publications on a single existing policy row.
        jdbc.queryForList("select id from billing_policy_versions order by revision asc limit 1 for update");
        java.sql.Timestamp lastEffective = jdbc.queryForObject(
            "select max(effective_at) from billing_policy_versions", java.sql.Timestamp.class);
        if (lastEffective != null && !request.effectiveAt().isAfter(lastEffective.toInstant())) {
            throw badRequest("A new policy must become effective after already published versions.");
        }
        Integer rev = jdbc.queryForObject("select max(revision) from billing_policy_versions", Integer.class);
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into billing_policy_versions
            (id, revision, onboarding_mode, trial_days, grace_days, effective_at, created_at, created_by_user_id)
            values (?, ?, ?, ?, ?, ?, ?, ?)
            """, id, rev + 1, request.onboardingMode(), request.trialDays(),
            request.graceDays(), java.sql.Timestamp.from(request.effectiveAt()),
            java.sql.Timestamp.from(clock.now()), actor.userId());
        audit.securityEvent(actor.userId(), "BILLING_POLICY_PUBLISHED", "BILLING_POLICY_VERSION", id,
            requestIds.current(), Map.of("revision", rev + 1));
        outbox.append(new OutboxEventIntent("BILLING_POLICY_PUBLISHED", "BILLING_POLICY_VERSION", id,
            Map.of("revision", rev + 1)));
        return new Policy(id, rev + 1, request.onboardingMode(),
            request.trialDays(), request.graceDays(), request.effectiveAt());
    }

    private static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Billing plan not found.");
    }

    public record PlanOffer(UUID id, String code, String name, int revision, long priceVnd,
                            int cycleMonths, Instant effectiveAt) {}
    public record PriceChange(long priceVnd, int cycleMonths, Instant effectiveAt) {}
    public record Policy(UUID id, int revision, String onboardingMode, int trialDays,
                         int graceDays, Instant effectiveAt) {}
    public record PolicyChange(String onboardingMode, int trialDays, int graceDays, Instant effectiveAt) {}
}
