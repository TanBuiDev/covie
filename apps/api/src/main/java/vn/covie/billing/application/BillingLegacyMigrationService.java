package vn.covie.billing.application;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

/**
 * Explicit, idempotent grants for pre-existing merchants. Never turn on enforcement
 * as part of migration. An operator must approve cutoff and deadline beforehand.
 */
@Service
public class BillingLegacyMigrationService {
    private final JdbcTemplate jdbc;
    private final AdminAccessPolicy admins;
    private final SystemSettingResolver settings;
    private final AppClock clock;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;
    private final boolean opsApproved;

    public BillingLegacyMigrationService(JdbcTemplate jdbc, AdminAccessPolicy admins,
            SystemSettingResolver settings, AppClock clock, AuditLogWriter audit,
            RequestIdAccessor requestIds,
            @Value("${COVIE_BILLING_LEGACY_GRANTS_APPROVED:false}") boolean opsApproved) {
        this.jdbc = jdbc; this.admins = admins; this.settings = settings; this.clock = clock;
        this.audit = audit; this.requestIds = requestIds; this.opsApproved = opsApproved;
    }

    @Transactional(readOnly = true)
    public Preview preview(Instant createdBefore) {
        admins.requireAdmin();
        validateCutoff(createdBefore, clock.now());
        Integer eligible = jdbc.queryForObject("""
            select count(*) from merchant_profiles
            where deleted_at is null and created_at < ?
            """, Integer.class, Timestamp.from(createdBefore));
        Integer alreadyGranted = jdbc.queryForObject("""
            select count(*) from billing_entitlement_grants g
            join merchant_profiles m on m.id = g.merchant_id
            where g.source_type='LEGACY' and m.deleted_at is null and m.created_at < ?
            """, Integer.class, Timestamp.from(createdBefore));
        return new Preview(eligible == null ? 0 : eligible,
            alreadyGranted == null ? 0 : alreadyGranted, createdBefore);
    }

    @Transactional
    public Result grant(GrantRequest request) {
        var actor = admins.requireAdmin();
        Instant now = clock.now();
        if (!opsApproved) throw reject("Legacy billing grants require an explicit operations approval.");
        if (settings.bool("billing.enforcement_enabled", SystemSettingContext.globalOnly())) {
            throw reject("Turn enforcement off before migrating existing shops.");
        }
        if (request == null) throw reject("Grant request is required.");
        validateCutoff(request.createdBefore(), now);
        if (request.validUntil() == null || !request.validUntil().isAfter(now)
                || request.validUntil().isAfter(now.plus(Duration.ofDays(3650)))) {
            throw reject("Grant expiry must be in the next 10 years.");
        }
        if (request.reason() == null || request.reason().isBlank() || request.reason().length() > 400) {
            throw reject("A documented reason is required.");
        }
        int created = jdbc.update("""
            insert into billing_entitlement_grants
              (id, merchant_id, source_type, source_id, valid_from, valid_until,
               reason, created_by_user_id, created_at)
            select gen_random_uuid(), m.id, 'LEGACY', m.id, ?, ?, ?, ?, ?
            from merchant_profiles m
            where m.deleted_at is null and m.created_at < ?
            on conflict (source_type, source_id) do nothing
            """, Timestamp.from(now), Timestamp.from(request.validUntil()), request.reason(),
            actor.userId(), Timestamp.from(now), Timestamp.from(request.createdBefore()));
        audit.securityEvent(actor.userId(), "BILLING_LEGACY_ENTITLEMENTS_GRANTED",
            "BILLING_LEGACY_GRANT", null, requestIds.current(),
            Map.of("created", created, "cutoff", request.createdBefore().toString(),
                "validUntil", request.validUntil().toString(), "reason", request.reason()));
        return new Result(created, request.validUntil());
    }

    static void validateCutoff(Instant cutoff, Instant now) {
        if (cutoff == null || now == null || cutoff.isAfter(now)) {
            throw reject("Cutoff must be an existing past or present timestamp.");
        }
    }
    private static ApiException reject(String text) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, text);
    }
    public record Preview(int eligibleMerchants, int previouslyGranted, Instant createdBefore) {}
    public record GrantRequest(Instant createdBefore, Instant validUntil, String reason) {}
    public record Result(int newlyGranted, Instant validUntil) {}
}
