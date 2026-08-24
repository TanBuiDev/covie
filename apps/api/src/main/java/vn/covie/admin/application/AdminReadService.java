package vn.covie.admin.application;

import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.admin.api.dto.AdminMerchantSummaryResponse;
import vn.covie.admin.api.dto.AdminOrderSummaryResponse;
import vn.covie.admin.api.dto.AdminPageResponse;
import vn.covie.admin.api.dto.AdminUserSummaryResponse;
import vn.covie.admin.persistence.AdminMerchantReadRepository;
import vn.covie.admin.persistence.AdminOrderReadRepository;
import vn.covie.admin.persistence.AdminUserReadRepository;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.orders.api.dto.OrderDetailResponse;
import vn.covie.orders.application.OrderReadService;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public final class AdminReadService {
    private static final Set<String> SORTS=Set.of("CREATED_DESC","CREATED_ASC","UPDATED_DESC");
    private final AdminAccessPolicy admins; private final AdminPageRequestValidator pages; private final AdminUserReadRepository users; private final AdminMerchantReadRepository merchants; private final AdminOrderReadRepository orders; private final OrderReadService orderReads;
    public AdminReadService(AdminAccessPolicy admins,AdminPageRequestValidator pages,AdminUserReadRepository users,AdminMerchantReadRepository merchants,AdminOrderReadRepository orders,OrderReadService orderReads){this.admins=admins;this.pages=pages;this.users=users;this.merchants=merchants;this.orders=orders;this.orderReads=orderReads;}
    public AdminPageResponse<AdminUserSummaryResponse> users(int page,int size,String status,String q,String sort){admins.requireAdmin();var p=pages.validate(page,size,sort,SORTS,"CREATED_DESC");var r=users.find(p.page(),p.size(),status,q,p.sort());return page(r.items(),r.total(),p.page(),p.size());}
    public AdminUserSummaryResponse user(UUID id){admins.requireAdmin();return users.findById(id).orElseThrow(AdminReadService::notFound);}
    public AdminPageResponse<AdminMerchantSummaryResponse> merchants(int page,int size,String status,String moderation,String q,String sort){admins.requireAdmin();var p=pages.validate(page,size,sort,SORTS,"CREATED_DESC");var r=merchants.find(p.page(),p.size(),status,moderation,q,p.sort());return page(r.items(),r.total(),p.page(),p.size());}
    public AdminMerchantSummaryResponse merchant(UUID id){admins.requireAdmin();return merchants.findById(id).orElseThrow(AdminReadService::notFound);}
    public AdminPageResponse<AdminOrderSummaryResponse> orders(int page,int size,String status,String type,UUID merchantId,UUID buyerId,String code,String sort){admins.requireAdmin();var p=pages.validate(page,size,sort,SORTS,"CREATED_DESC");var r=orders.find(p.page(),p.size(),status,type,merchantId,buyerId,code,p.sort());return page(r.items(),r.total(),p.page(),p.size());}
    public OrderDetailResponse order(UUID id){admins.requireAdmin();return orderReads.detail(id);}
    private static <T> AdminPageResponse<T> page(java.util.List<T> items,long total,int page,int size){return new AdminPageResponse<>(items,total,page,size,(int)Math.ceil(total/(double)size));}
    private static ApiException notFound(){return new ApiException(ErrorCode.RESOURCE_NOT_FOUND,HttpStatus.NOT_FOUND,"Resource not found.");}
}
