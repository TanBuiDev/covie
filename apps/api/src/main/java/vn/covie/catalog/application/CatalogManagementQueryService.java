package vn.covie.catalog.application;

import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.api.dto.CatalogItemManagementSummaryResponse;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;

@Service
public class CatalogManagementQueryService {
    private final MerchantAccessPolicy access;
    private final CatalogItemRepository items;

    public CatalogManagementQueryService(MerchantAccessPolicy access, CatalogItemRepository items) {
        this.access = access;
        this.items = items;
    }

    @Transactional(readOnly = true)
    public List<CatalogItemManagementSummaryResponse> list(UUID merchantId) {
        access.require(merchantId, MerchantCapability.VIEW_MERCHANT);
        return items.findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(merchantId).stream()
                .map(CatalogManagementQueryService::summary)
                .toList();
    }

    private static CatalogItemManagementSummaryResponse summary(CatalogItemEntity item) {
        return new CatalogItemManagementSummaryResponse(item.getId(), item.getItemType(), item.getListingKind(),
                item.getName(), item.getListingStatus(), item.getVersion());
    }
}
