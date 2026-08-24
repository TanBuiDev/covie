package vn.covie.admin.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.admin.api.dto.AdminMerchantCommandResult;
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantStatus;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Service
public final class AdminMerchantService {
    private final AdminAccessPolicy admins; private final MerchantProfileRepository merchants; private final IdempotencyExecutor idempotency;
    private final AuditLogWriter audit; private final RequestIdAccessor requestIds; private final OutboxEventWriter outbox; private final AppClock clock;
    public AdminMerchantService(AdminAccessPolicy admins,MerchantProfileRepository merchants,IdempotencyExecutor idempotency,AuditLogWriter audit,
            RequestIdAccessor requestIds,OutboxEventWriter outbox,AppClock clock){this.admins=admins;this.merchants=merchants;this.idempotency=idempotency;this.audit=audit;this.requestIds=requestIds;this.outbox=outbox;this.clock=clock;}

    public AdminMerchantCommandResult suspend(UUID merchantId, AdminReasonRequest request, String key){
        CurrentActor actor=admins.requireAdmin(); return idempotency.execute(actor.userId(),"admin.merchant.suspend:"+merchantId,key,request,200,AdminMerchantCommandResult.class,
                () -> suspendLocked(actor,merchantId,request.reason()));
    }
    public AdminMerchantCommandResult restore(UUID merchantId,String key){
        CurrentActor actor=admins.requireAdmin(); return idempotency.execute(actor.userId(),"admin.merchant.restore:"+merchantId,key,Map.of(),200,AdminMerchantCommandResult.class,
                () -> restoreLocked(actor,merchantId));
    }
    private AdminMerchantCommandResult suspendLocked(CurrentActor actor,UUID merchantId,String reason){
        MerchantProfileEntity merchant=merchants.findActiveForUpdate(merchantId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Merchant was not found."));
        if (merchant.getMerchantStatus()!=MerchantStatus.ACTIVE && merchant.getMerchantStatus()!=MerchantStatus.PAUSED) throw state("Merchant is not suspendable.");
        merchant.suspend(clock.now()); write(actor,merchant,"ADMIN_MERCHANT_SUSPENDED",reason); return new AdminMerchantCommandResult(merchantId,merchant.getMerchantStatus());
    }
    private AdminMerchantCommandResult restoreLocked(CurrentActor actor,UUID merchantId){
        MerchantProfileEntity merchant=merchants.findActiveForUpdate(merchantId).orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Merchant was not found."));
        if (merchant.getMerchantStatus()!=MerchantStatus.SUSPENDED) throw state("Merchant is not SUSPENDED.");
        merchant.restore(clock.now()); write(actor,merchant,"ADMIN_MERCHANT_RESTORED",null); return new AdminMerchantCommandResult(merchantId,merchant.getMerchantStatus());
    }
    private void write(CurrentActor actor,MerchantProfileEntity merchant,String event,String reason){
        Map<String,Object> payload=reason==null?Map.of("merchantId",merchant.getId().toString()):Map.of("merchantId",merchant.getId().toString(),"reason",reason);
        audit.securityEvent(actor.userId(),event,"MERCHANT",merchant.getId(),requestIds.current(),payload); outbox.append(new OutboxEventIntent(event,"MERCHANT",merchant.getId(),payload));
    }
    private static ApiException state(String message){return new ApiException(ErrorCode.ADMIN_MERCHANT_STATE_CONFLICT,HttpStatus.CONFLICT,message);}
}
