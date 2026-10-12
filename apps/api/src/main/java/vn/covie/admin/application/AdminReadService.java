package vn.covie.admin.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.api.dto.*;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.merchants.model.*;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.orders.model.*;
import vn.covie.orders.persistence.OrderRepository; import vn.covie.orders.persistence.OrderEventRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.AccountStatus;
import vn.covie.users.persistence.SystemRoleAssignmentRepository;
import vn.covie.users.persistence.UserRepository;

@Service
public class AdminReadService {
    private final AdminAccessPolicy access; private final AdminPageRequestValidator pages; private final UserRepository users; private final SystemRoleAssignmentRepository roles; private final MerchantProfileRepository merchants; private final OrderRepository orders; private final OrderEventRepository events;
    public AdminReadService(AdminAccessPolicy access,AdminPageRequestValidator pages,UserRepository users,SystemRoleAssignmentRepository roles,MerchantProfileRepository merchants,OrderRepository orders,OrderEventRepository events){this.access=access;this.pages=pages;this.users=users;this.roles=roles;this.merchants=merchants;this.orders=orders;this.events=events;}
    @Transactional(readOnly=true) public AdminPageResponse<AdminUserSummaryResponse> users(int page,int size,AccountStatus status,String q,String sort){access.requireAdmin();var p=page(page,size,sort);var result=users.searchAdmin(status,blank(q),p);return new AdminPageResponse<>(result.map(u->new AdminUserSummaryResponse(u.getId(),u.getDisplayName(),u.getAccountStatus(),roles.findAllByUserId(u.getId()).stream().map(r->r.getRole().name()).collect(java.util.stream.Collectors.toUnmodifiableSet()),u.getCreatedAt(),u.getUpdatedAt())).getContent(),result.getTotalElements(),p.getPageNumber(),p.getPageSize());}
    @Transactional(readOnly=true) public AdminUserSummaryResponse user(UUID id){access.requireAdmin();var u=users.findById(id).orElseThrow(this::notFound);return new AdminUserSummaryResponse(u.getId(),u.getDisplayName(),u.getAccountStatus(),roles.findAllByUserId(id).stream().map(r->r.getRole().name()).collect(java.util.stream.Collectors.toUnmodifiableSet()),u.getCreatedAt(),u.getUpdatedAt());}
    @Transactional(readOnly=true) public AdminPageResponse<AdminMerchantSummaryResponse> merchants(int page,int size,MerchantStatus status,MerchantModerationStatus moderationStatus,String q,String sort){access.requireAdmin();var p=page(page,size,sort);var result=merchants.searchAdmin(status,moderationStatus,blank(q),p);return new AdminPageResponse<>(result.map(m->new AdminMerchantSummaryResponse(m.getId(),m.getName(),m.getSlug(),m.getMerchantStatus(),m.getModerationStatus(),m.getCreatedAt(),m.getUpdatedAt())).getContent(),result.getTotalElements(),p.getPageNumber(),p.getPageSize());}
    @Transactional(readOnly=true) public AdminMerchantSummaryResponse merchant(UUID id){access.requireAdmin();var m=merchants.findByIdAndDeletedAtIsNull(id).orElseThrow(this::notFound);return new AdminMerchantSummaryResponse(m.getId(),m.getName(),m.getSlug(),m.getMerchantStatus(),m.getModerationStatus(),m.getCreatedAt(),m.getUpdatedAt());}
    @Transactional(readOnly=true) public AdminPageResponse<AdminOrderSummaryResponse> orders(int page,int size,OrderStatus status,OrderType type,UUID merchantId,UUID buyerUserId,String orderCode,String sort){access.requireAdmin();var p=page(page,size,sort);var result=orders.searchAdmin(status,type,merchantId,buyerUserId,blank(orderCode),p);return new AdminPageResponse<>(result.map(o->new AdminOrderSummaryResponse(o.getId(),o.getOrderCode(),o.getOrderType(),o.getStatus(),o.getBuyerUserId(),o.getMerchantProfileId(),o.getCreatedAt(),o.getUpdatedAt())).getContent(),result.getTotalElements(),p.getPageNumber(),p.getPageSize());}
    @Transactional(readOnly=true) public java.util.List<AdminOrderEventResponse> orderEvents(UUID orderId){access.requireAdmin();if(!orders.existsById(orderId))throw notFound();return events.findAllByOrderIdOrderByCreatedAtAscIdAsc(orderId).stream().map(e->new AdminOrderEventResponse(e.getId(),e.getEventType(),e.getActorUserId(),e.getActorContext(),e.getFromStatus(),e.getToStatus(),e.getReasonCode(),e.getCreatedAt())).toList();}
    private PageRequest page(int page,int size,String sort){var request=pages.validate(page,size,sort);return PageRequest.of(request.page(),request.size(),switch(request.sort()){case "CREATED_ASC"->Sort.by("createdAt", "id").ascending();case "UPDATED_DESC"->Sort.by("updatedAt", "id").descending();default->Sort.by("createdAt", "id").descending();});}
    private String blank(String value){return value==null||value.isBlank()?null:value.trim();}
    private ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");}
}
