package vn.covie.admin.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.admin.api.dto.AdminListingSummaryResponse;
import vn.covie.admin.api.dto.AdminNeedSummaryResponse;
import vn.covie.admin.api.dto.AdminPageResponse;
import vn.covie.admin.api.dto.AdminShopLocationResponse;
import vn.covie.admin.persistence.AdminOperationsReadRepository;
import vn.covie.admin.security.AdminAccessPolicy;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.model.MerchantStatus;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.model.NeedType;

@Service
public class AdminOperationsReadService {
    private final AdminAccessPolicy access;
    private final AdminPageRequestValidator pages;
    private final AdminOperationsReadRepository reads;

    public AdminOperationsReadService(
            AdminAccessPolicy access, AdminPageRequestValidator pages, AdminOperationsReadRepository reads) {
        this.access = access;
        this.pages = pages;
        this.reads = reads;
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminShopLocationResponse> shops(
            int page, int size, MerchantStatus merchantStatus, MerchantLocationStatus locationStatus, String q, String sort) {
        access.requireAdmin();
        var request = pages.validate(page, size, sort);
        var result = reads.shops(request.page(), request.size(), name(merchantStatus), name(locationStatus), q, request.sort());
        return response(result, request);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminListingSummaryResponse> listings(
            int page, int size, ListingStatus status, CatalogModerationStatus moderationStatus,
            CatalogItemType type, String q, String sort) {
        access.requireAdmin();
        var request = pages.validate(page, size, sort);
        var result = reads.listings(request.page(), request.size(), name(status), name(moderationStatus),
                name(type), q, request.sort());
        return response(result, request);
    }

    @Transactional(readOnly = true)
    public AdminPageResponse<AdminNeedSummaryResponse> needs(
            int page, int size, NeedStatus status, NeedType type, String q, String sort) {
        access.requireAdmin();
        var request = pages.validate(page, size, sort);
        var result = reads.needs(request.page(), request.size(), name(status), name(type), q, request.sort());
        return response(result, request);
    }

    private static <T> AdminPageResponse<T> response(
            AdminOperationsReadRepository.PageResult<T> result, AdminPageRequestValidator.AdminPageRequest request) {
        return new AdminPageResponse<>(result.items(), result.total(), request.page(), request.size());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
