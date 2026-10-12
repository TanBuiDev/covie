package vn.covie.admin.application;
import java.util.Map;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.orders.persistence.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.api.dto.*;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.audit.AuditLogWriter;
import vn.covie.reputation.application.ReputationRebuildService;
import vn.covie.reviews.application.RatingRebuildService;
import vn.covie.shared.web.RequestIdAccessor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
@Service public class AdminMaintenanceService {
 private final CatalogItemRepository catalog;private final MerchantProfileRepository merchants;private final OrderRepository orders;
 private final AdminAccessPolicy access;private final RatingRebuildService ratings;private final ReputationRebuildService reputation;private final AuditLogWriter audit;private final RequestIdAccessor requestIds;
 public AdminMaintenanceService(AdminAccessPolicy access,RatingRebuildService ratings,ReputationRebuildService reputation,AuditLogWriter audit,RequestIdAccessor requestIds,CatalogItemRepository catalog,MerchantProfileRepository merchants,OrderRepository orders){this.catalog=catalog;this.merchants=merchants;this.orders=orders;this.access=access;this.ratings=ratings;this.reputation=reputation;this.audit=audit;this.requestIds=requestIds;}
 @Transactional public MaintenanceCommandResult rebuildRatings(RatingRebuildRequest request,String reason){var actor=access.requireAdmin();if(request.catalogItemId()!=null?!catalog.existsById(request.catalogItemId()):!merchants.existsById(request.merchantId()))throw notFound();String target=request.catalogItemId()!=null?"catalog:"+request.catalogItemId():"merchant:"+request.merchantId();if(request.catalogItemId()!=null)ratings.rebuildCatalogItem(request.catalogItemId());else ratings.rebuildMerchant(request.merchantId());audit.securityEvent(actor.userId(),"ADMIN_RATING_REBUILD","MAINTENANCE",null,requestIds.current(),Map.of("target",target,"reason",reason));return new MaintenanceCommandResult("RATING_REBUILD",target);}
 @Transactional public MaintenanceCommandResult rebuildReputation(ReputationRebuildRequest request,String reason){var actor=access.requireAdmin();if(!request.rebuildAll()&&!orders.existsById(request.orderId()))throw notFound();String target=request.rebuildAll()?"all":"order:"+request.orderId();try {if(request.rebuildAll())reputation.rebuildAllForMaintenance();else reputation.rebuildOrder(request.orderId());} catch(IllegalStateException ex) {throw new ApiException(ErrorCode.MAINTENANCE_REBUILD_DISABLED,HttpStatus.FORBIDDEN,ex.getMessage());} audit.securityEvent(actor.userId(),"ADMIN_REPUTATION_REBUILD","MAINTENANCE",null,requestIds.current(),Map.of("target",target,"reason",reason));return new MaintenanceCommandResult("REPUTATION_REBUILD",target);}
 private ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Maintenance target not found.");}
}
