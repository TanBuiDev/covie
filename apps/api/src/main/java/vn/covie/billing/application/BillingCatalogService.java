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

    @Transactional(readOnly = true)
    public List<PlanOffer> latestPublishedPlans() {
        admins.requireAdmin();
        return jdbc.query("""
            select distinct on (p.code) v.id, p.code, p.display_name, v.revision,
                   v.price_vnd, v.cycle_months, v.effective_at
            from billing_plans p join billing_plan_versions v on v.plan_code=p.code
            where p.retired_at is null order by p.code, v.revision desc
            """, (result, rowNumber) -> new PlanOffer((UUID) result.getObject("id"), result.getString("code"),
                result.getString("display_name"), result.getInt("revision"), result.getLong("price_vnd"),
                result.getInt("cycle_months"), result.getTimestamp("effective_at").toInstant()));
    }

    @Transactional(readOnly = true)
    public Policy latestPublishedPolicy() {
        admins.requireAdmin();
        return jdbc.query("""
            select id, revision, onboarding_mode, trial_days, grace_days, effective_at
            from billing_policy_versions order by revision desc limit 1
            """, result -> {
                if (!result.next()) throw new IllegalStateException("No published billing policy.");
                return new Policy((UUID) result.getObject("id"), result.getInt("revision"),
                    result.getString("onboarding_mode"), result.getInt("trial_days"), result.getInt("grace_days"),
                    result.getTimestamp("effective_at").toInstant());
            });
    }

    @Transactional(readOnly = true)
    public List<PriceHistory> priceHistory(String planCode, int limit, int offset) {
        admins.requireAdmin();
        validateHistoryPage(limit, offset);
        if (planCode == null || !planCode.matches("[A-Z0-9_]{1,60}")) throw badRequest("Invalid plan.");
        if (jdbc.queryForObject("select count(*) from billing_plans where code=?", Long.class, planCode) == 0) throw notFound();
        return jdbc.query("""
            select v.id, v.plan_code, p.display_name, v.revision, v.price_vnd,
                   v.cycle_months, v.effective_at, v.published_at, p.retired_at
            from billing_plan_versions v join billing_plans p on p.code=v.plan_code
            where v.plan_code=? order by v.revision desc limit ? offset ?
            """, (result, rowNumber) -> new PriceHistory(new PlanOffer((UUID) result.getObject("id"),
                result.getString("plan_code"), result.getString("display_name"), result.getInt("revision"),
                result.getLong("price_vnd"), result.getInt("cycle_months"), result.getTimestamp("effective_at").toInstant()),
                result.getTimestamp("published_at").toInstant(), result.getTimestamp("retired_at") != null), planCode, limit, offset);
    }

    @Transactional(readOnly = true)
    public List<PolicyHistory> policyHistory(int limit, int offset) {
        admins.requireAdmin();
        validateHistoryPage(limit, offset);
        return jdbc.query("""
            select id, revision, onboarding_mode, trial_days, grace_days, effective_at, created_at
            from billing_policy_versions order by revision desc limit ? offset ?
            """, (result, rowNumber) -> new PolicyHistory(new Policy((UUID) result.getObject("id"),
                result.getInt("revision"), result.getString("onboarding_mode"), result.getInt("trial_days"),
                result.getInt("grace_days"), result.getTimestamp("effective_at").toInstant()),
                result.getTimestamp("created_at").toInstant()), limit, offset);
    }

    private static void validateHistoryPage(int limit, int offset) {
        if (limit < 1 || limit > 100 || offset < 0 || offset > 500_000) throw badRequest("Invalid billing history page.");
    }

    @Transactional(readOnly = true)
    public PricePublicationPreview previewPrice(String planCode, PriceChange request, int expectedRevision) {
        admins.requireAdmin();
        if (request == null || planCode == null || !planCode.matches("[A-Z0-9_]{1,60}")
                || request.priceVnd() < 0 || request.priceVnd() > 1_000_000_000L
                || request.cycleMonths() < 1 || request.cycleMonths() > 120
                || request.effectiveAt() == null || !request.effectiveAt().isAfter(clock.now())) {
            throw badRequest("Invalid price or effective date.");
        }
        PlanOffer previous = latestPublishedPlans().stream().filter(plan -> plan.code().equals(planCode))
            .findFirst().orElseThrow(BillingCatalogService::notFound);
        if (previous.revision() != expectedRevision) throw revisionConflict();
        if (!request.effectiveAt().isAfter(previous.effectiveAt())) {
            throw badRequest("A new price must become effective after already published versions.");
        }
        long merchants = jdbc.queryForObject("select count(*) from merchant_subscriptions where plan_code=?", Long.class, planCode);
        return new PricePublicationPreview(previous, request, merchants, clock.now());
    }

    @Transactional(readOnly = true)
    public PolicyPublicationPreview previewPolicy(PolicyChange request, int expectedRevision) {
        admins.requireAdmin();
        if (request == null || request.onboardingMode() == null
                || !List.of("PAY_BEFORE_SELL", "TRIAL").contains(request.onboardingMode())
                || request.trialDays() < 0 || request.trialDays() > 365
                || request.graceDays() < 0 || request.graceDays() > 60
                || request.effectiveAt() == null || !request.effectiveAt().isAfter(clock.now())) {
            throw badRequest("Invalid billing policy.");
        }
        Policy previous = latestPublishedPolicy();
        if (previous.revision() != expectedRevision) throw revisionConflict();
        if (!request.effectiveAt().isAfter(previous.effectiveAt())) {
            throw badRequest("A new policy must become effective after already published versions.");
        }
        long merchants = jdbc.queryForObject("select count(*) from merchant_subscriptions", Long.class);
        return new PolicyPublicationPreview(previous, request, merchants, clock.now());
    }

    /** Admin creates independent plans, with their own immutable cycle and price history. */
    @Transactional
    public PlanOffer createPlan(NewPlan request) {
        var actor = admins.requireAdmin();
        if (request == null || request.code() == null || !request.code().matches("[A-Z0-9_]{1,60}")
                || request.displayName() == null || request.displayName().isBlank()
                || request.displayName().length() > 180
                || request.priceVnd() <= 0 || request.priceVnd() > 1_000_000_000L
                || request.cycleMonths() < 1 || request.cycleMonths() > 120
                || request.effectiveAt() == null || request.effectiveAt().isBefore(clock.now())) {
            throw badRequest("Invalid new plan.");
        }
        // Database primary key prevents duplicate plan creation across concurrent Admin requests.
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("insert into billing_plans(code,display_name,created_at) values (?,?,?)",
                request.code(), request.displayName().trim(), java.sql.Timestamp.from(clock.now()));
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.CONFLICT,
                "A plan with this code already exists.");
        }
        jdbc.update("""
            insert into billing_plan_versions
              (id,plan_code,revision,price_vnd,cycle_months,effective_at,published_at,created_by_user_id)
            values (?,?,1,?,?,?,?,?)
            """, id, request.code(), request.priceVnd(), request.cycleMonths(),
            java.sql.Timestamp.from(request.effectiveAt()), java.sql.Timestamp.from(clock.now()), actor.userId());
        audit.securityEvent(actor.userId(), "BILLING_PLAN_CREATED", "BILLING_PLAN_VERSION", id,
            requestIds.current(), Map.of("planCode", request.code(), "revision", 1));
        outbox.append(new OutboxEventIntent("BILLING_PLAN_CREATED", "BILLING_PLAN_VERSION", id,
            Map.of("planCode", request.code())));
        return new PlanOffer(id, request.code(), request.displayName().trim(), 1,
            request.priceVnd(), request.cycleMonths(), request.effectiveAt());
    }

    /** Retiring a plan prevents new purchases; paid invoice snapshots remain valid. */
    @Transactional
    public void retirePlan(String planCode) {
        var actor = admins.requireAdmin();
        if (planCode == null || !planCode.matches("[A-Z0-9_]{1,60}")) throw badRequest("Invalid plan.");
        int count = jdbc.update("""
            update billing_plans set retired_at=?
            where code=? and retired_at is null
            """, java.sql.Timestamp.from(clock.now()), planCode);
        if (count != 1) throw notFound();
        UUID event = UUID.randomUUID();
        audit.securityEvent(actor.userId(), "BILLING_PLAN_RETIRED", "BILLING_PLAN", event,
            requestIds.current(), Map.of("planCode", planCode));
        outbox.append(new OutboxEventIntent("BILLING_PLAN_RETIRED", "BILLING_PLAN", event,
            Map.of("planCode", planCode)));
    }

    @Transactional
    public PlanOffer publishPrice(String planCode, PriceChange request, int expectedRevision) {
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
        Integer rev = jdbc.queryForObject(
            "select coalesce(max(revision), 0) from billing_plan_versions where plan_code = ?",
            Integer.class, planCode);
        if (rev == null || rev != expectedRevision) {
            throw revisionConflict();
        }
        java.sql.Timestamp lastEffective = jdbc.queryForObject("""
            select max(effective_at) from billing_plan_versions where plan_code = ?
            """, java.sql.Timestamp.class, planCode);
        if (lastEffective != null && !request.effectiveAt().isAfter(lastEffective.toInstant())) {
            throw badRequest("A new price must become effective after already published versions.");
        }
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
    public Policy publishPolicy(PolicyChange request, int expectedRevision) {
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
        Integer rev = jdbc.queryForObject("select max(revision) from billing_policy_versions", Integer.class);
        if (rev == null || rev != expectedRevision) throw revisionConflict();
        java.sql.Timestamp lastEffective = jdbc.queryForObject(
            "select max(effective_at) from billing_policy_versions", java.sql.Timestamp.class);
        if (lastEffective != null && !request.effectiveAt().isAfter(lastEffective.toInstant())) {
            throw badRequest("A new policy must become effective after already published versions.");
        }
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

    public static int parseIfMatch(String header) {
        if (header == null || header.length() < 3
                || header.charAt(0) != '"' || header.charAt(header.length()-1) != '"') {
            throw badRequest("If-Match must contain a quoted positive revision.");
        }
        String value = header.substring(1, header.length()-1);
        if (!value.matches("[1-9][0-9]{0,8}"))
            throw badRequest("If-Match contains an invalid revision.");
        return Integer.parseInt(value);
    }

    private static ApiException revisionConflict() {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.CONFLICT,
            "Billing configuration changed since it was loaded. Reload and review the latest revision.");
    }
    private static ApiException badRequest(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Billing plan not found.");
    }

    public record NewPlan(String code, String displayName, long priceVnd,
                          int cycleMonths, Instant effectiveAt) {}
    public record PlanOffer(UUID id, String code, String name, int revision, long priceVnd,
                            int cycleMonths, Instant effectiveAt) {}
    public record PriceChange(long priceVnd, int cycleMonths, Instant effectiveAt) {}
    public record Policy(UUID id, int revision, String onboardingMode, int trialDays,
                         int graceDays, Instant effectiveAt) {}
    public record PolicyChange(String onboardingMode, int trialDays, int graceDays, Instant effectiveAt) {}
    public record PricePublicationPreview(PlanOffer previous, PriceChange proposed,
                                           long referencingMerchants, Instant observedAt) {}
    public record PolicyPublicationPreview(Policy previous, PolicyChange proposed,
                                            long referencingMerchants, Instant observedAt) {}
    public record PriceHistory(PlanOffer version, Instant publishedAt, boolean retired) {}
    public record PolicyHistory(Policy version, Instant publishedAt) {}
}
