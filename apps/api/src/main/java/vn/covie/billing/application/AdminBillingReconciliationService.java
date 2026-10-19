package vn.covie.billing.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.audit.AuditLogWriter;
import vn.covie.billing.reconciliation.BillingReconciliationService;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.idempotency.IdempotencyHasher;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Service
public class AdminBillingReconciliationService {
    private final JdbcTemplate jdbc;
    private final BillingFinanceAccessPolicy access;
    private final BillingReconciliationService reconciliation;
    private final IdempotencyHasher hasher;
    private final ObjectMapper mapper;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;
    private final TransactionTemplate transactions;

    public AdminBillingReconciliationService(JdbcTemplate jdbc, BillingFinanceAccessPolicy access,
            BillingReconciliationService reconciliation, IdempotencyHasher hasher, ObjectMapper mapper,
            UuidV7Generator ids, AppClock clock, AuditLogWriter audit, RequestIdAccessor requestIds,
            PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.access = access;
        this.reconciliation = reconciliation;
        this.hasher = hasher;
        this.mapper = mapper.copy().disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
        this.requestIds = requestIds;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public AdminBillingReconciliationResponse reconcile(UUID invoiceId, AdminBillingReconciliationRequest request,
            String key) {
        UUID actor = access.requireOperator().userId();
        if (key == null || key.isBlank()) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
            HttpStatus.BAD_REQUEST, "Idempotency-Key is required.");
        if (key.length() > 200) throw new ApiException(ErrorCode.MALFORMED_REQUEST,
            HttpStatus.BAD_REQUEST, "Idempotency-Key is too long.");
        String operation = "ADMIN_BILLING_RECONCILIATION:" + invoiceId;
        String requestHash = hasher.sha256(request);
        Claim claim = transactions.execute(transaction -> claim(actor, invoiceId, operation, key, requestHash, request));
        if (claim.cached() != null) return claim.cached();
        Invoice invoice = invoice(invoiceId);
        var observation = "PENDING_PAYMENT".equals(invoice.status())
            ? reconciliation.queryProvider(invoice.candidate())
            : reconciliation.inspectProvider(invoice.candidate());
        return transactions.execute(transaction -> complete(actor, invoiceId, claim, observation));
    }

    private Claim claim(UUID actor, UUID invoiceId, String operation, String key, String requestHash,
            AdminBillingReconciliationRequest request) {
        jdbc.query("select pg_advisory_xact_lock(?)",
            statement -> statement.setLong(1, hasher.advisoryLockKey(actor, operation, key)), result -> {});
        var existing = jdbc.query("""
            select id,request_hash,status,response_body::text from idempotency_records
            where actor_id=? and operation=? and idempotency_key_hash=? for update
            """, (result, row) -> new Stored((UUID) result.getObject("id"), result.getString("request_hash"),
                result.getString("status"), result.getString("response_body")), actor, operation, hasher.sha256(key));
        UUID operationId = existing.isEmpty() ? ids.generate() : existing.getFirst().id();
        if (!existing.isEmpty()) {
            Stored stored = existing.getFirst();
            if (!stored.requestHash().equals(requestHash)) throw new ApiException(
                ErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_REQUEST, HttpStatus.CONFLICT,
                "Idempotency key was already used with another request.");
            JsonNode body = parse(stored.body());
            if ("COMPLETED".equals(stored.status())) return new Claim(operationId, null,
                mapper.convertValue(body, AdminBillingReconciliationResponse.class));
            if (body.hasNonNull("leaseUntil") && Instant.parse(body.path("leaseUntil").asText()).isAfter(clock.now()))
                throw conflict("Reconciliation is already in progress; retry the same key later.");
        }
        invoice(invoiceId);
        UUID token = UUID.randomUUID();
        Instant now = clock.now();
        String lease = mapper.createObjectNode().put("leaseToken", token.toString())
            .put("leaseUntil", now.plusSeconds(30).toString()).toString();
        if (existing.isEmpty()) {
            jdbc.update("""
                insert into idempotency_records
                  (id,actor_id,operation,idempotency_key_hash,request_hash,status,response_body,created_at,expires_at)
                values (?,?,?,?,?,'IN_PROGRESS',?::jsonb,?,?)
                """, operationId, actor, operation, hasher.sha256(key), requestHash, lease,
                Timestamp.from(now), Timestamp.from(now.plus(Duration.ofHours(24))));
        } else {
            jdbc.update("update idempotency_records set response_body=?::jsonb where id=?", lease, operationId);
        }
        audit.securityEvent(actor, "BILLING_RECONCILIATION_REQUESTED", "BILLING_INVOICE", invoiceId,
            requestIds.current(), Map.of("operationId", operationId.toString(), "reason", request.reason().trim()));
        return new Claim(operationId, token, null);
    }

    private AdminBillingReconciliationResponse complete(UUID actor, UUID invoiceId, Claim claim,
            BillingReconciliationService.Observation observation) {
        var stored = jdbc.query("""
            select response_body::text from idempotency_records where id=? and status='IN_PROGRESS' for update
            """, (result, row) -> result.getString(1), claim.id());
        if (stored.size() != 1 || !claim.token().toString().equals(parse(stored.getFirst()).path("leaseToken").asText()))
            throw conflict("Reconciliation was reclaimed; retry the same key for its current result.");
        String status = invoice(invoiceId).status();
        String discrepancy = observation.discrepancy();
        if (!"PENDING_PAYMENT".equals(status)) {
            boolean consistent = ("PAID".equals(status) && "PAID".equals(observation.providerStatus())
                    && ("NONE".equals(discrepancy) || "PAID_PENDING_WEBHOOK".equals(discrepancy)))
                || ("VOID".equals(status) && "NONE".equals(discrepancy)
                    && ("EXPIRED".equals(observation.providerStatus()) || "CANCELLED".equals(observation.providerStatus())));
            if (consistent) discrepancy = "NONE";
            else if ("NONE".equals(discrepancy) || "PAID_PENDING_WEBHOOK".equals(discrepancy))
                discrepancy = "MANUAL_REVIEW_REQUIRED";
        }
        reconciliation.record(observation);
        var response = new AdminBillingReconciliationResponse(claim.id(), invoiceId, status,
            observation.providerStatus(), discrepancy, clock.now());
        audit.securityEvent(actor, "BILLING_RECONCILIATION_COMPLETED", "BILLING_INVOICE", invoiceId,
            requestIds.current(), Map.of("operationId", claim.id().toString(), "invoiceStatus", status,
                "providerStatus", observation.providerStatus(), "discrepancy", discrepancy));
        jdbc.update("""
            update idempotency_records set status='COMPLETED',response_status=200,response_body=?::jsonb
            where id=?
            """, mapper.valueToTree(response).toString(), claim.id());
        return response;
    }

    private Invoice invoice(UUID invoiceId) {
        var rows = jdbc.query("""
            select i.status,i.payos_order_code,i.amount_vnd,a.provider_payment_link_id
            from billing_invoices i join billing_payment_attempts a on a.invoice_id=i.id
            where i.id=? and a.provider='PAYOS' and i.payos_order_code is not null
            """, (result, row) -> new Invoice(result.getString("status"), new BillingReconciliationService.Candidate(
                invoiceId, result.getLong("payos_order_code"), result.getLong("amount_vnd"),
                result.getString("provider_payment_link_id"))), invoiceId);
        if (rows.size() != 1) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
            "Invoice with a payOS payment attempt was not found.");
        return rows.getFirst();
    }

    private JsonNode parse(String value) {
        try { return mapper.readTree(value); }
        catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException("Invalid stored reconciliation operation", exception);
        }
    }

    private static ApiException conflict(String message) {
        return new ApiException(ErrorCode.RESOURCE_VERSION_CONFLICT, HttpStatus.CONFLICT, message);
    }

    private record Stored(UUID id, String requestHash, String status, String body) {}
    private record Claim(UUID id, UUID token, AdminBillingReconciliationResponse cached) {}
    private record Invoice(String status, BillingReconciliationService.Candidate candidate) {}
    public record AdminBillingReconciliationRequest(@NotBlank @Size(max = 400) String reason) {}
    public record AdminBillingReconciliationResponse(UUID operationId, UUID invoiceId, String invoiceStatus,
        String providerStatus, String discrepancy, Instant checkedAt) {}
}
