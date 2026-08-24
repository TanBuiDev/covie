package vn.covie.admin.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.api.dto.AdminMerchantCommandResult;
import vn.covie.admin.api.dto.AdminReasonRequest;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Service
public class AdminMerchantService {
    private final AdminAccessPolicy access; private final MerchantProfileRepository merchants; private final AuditLogWriter audit;
    private final OutboxEventWriter outbox; private final AppClock clock; private final RequestIdAccessor requestIds;
    public AdminMerchantService(AdminAccessPolicy access, MerchantProfileRepository merchants, AuditLogWriter audit, OutboxEventWriter outbox, AppClock clock, RequestIdAccessor requestIds) { this.access=access;this.merchants=merchants;this.audit=audit;this.outbox=outbox;this.clock=clock;this.requestIds=requestIds; }
    @Transactional public AdminMerchantCommandResult suspend(UUID merchantId, AdminReasonRequest request) { return mutate(merchantId,"SUSPENDED",request); }
    @Transactional public AdminMerchantCommandResult restore(UUID merchantId) { return mutate(merchantId,"RESTORED",null); }
    private AdminMerchantCommandResult mutate(UUID id,String operation,AdminReasonRequest request) { var actor=access.requireAdmin(); MerchantProfileEntity merchant=merchants.findActiveForUpdate(id).orElseThrow(()->new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Merchant was not found.")); try { if(operation.equals("SUSPENDED")) merchant.suspend(clock.now()); else merchant.restore(clock.now()); } catch(IllegalStateException ex){throw new ApiException(ErrorCode.ADMIN_MERCHANT_STATE_CONFLICT,HttpStatus.CONFLICT,ex.getMessage());} Map<String,Object> metadata=request==null?Map.of():Map.of("reasonCode",request.reasonCode()); audit.securityEvent(actor.userId(),"ADMIN_MERCHANT_"+operation,"MERCHANT",id,requestIds.current(),metadata); outbox.append(new OutboxEventIntent("ADMIN_MERCHANT_"+operation,"MERCHANT",id,metadata)); return new AdminMerchantCommandResult(id,merchant.getMerchantStatus()); }
}
