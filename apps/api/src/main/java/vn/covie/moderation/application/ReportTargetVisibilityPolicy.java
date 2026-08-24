package vn.covie.moderation.application;

import java.util.UUID;
import org.springframework.stereotype.Service;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.merchants.model.MerchantModerationStatus;
import vn.covie.merchants.model.MerchantStatus;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.needs.application.NeedMatchingService;
import vn.covie.reviews.model.ReviewStatus;
import vn.covie.reviews.persistence.ReviewRepository;

@Service
public class ReportTargetVisibilityPolicy {
    private final MerchantProfileRepository merchants; private final CatalogItemRepository catalog; private final ReviewRepository reviews; private final NeedMatchingService needs;
    public ReportTargetVisibilityPolicy(MerchantProfileRepository merchants,CatalogItemRepository catalog,ReviewRepository reviews,NeedMatchingService needs){this.merchants=merchants;this.catalog=catalog;this.reviews=reviews;this.needs=needs;}
    public boolean isVisible(UUID actorId, ModerationTargetType type, UUID targetId, UUID contextMerchantId) {
        return switch(type) {
            case MERCHANT -> merchants.findByIdAndDeletedAtIsNull(targetId).map(this::visibleMerchant).orElse(false);
            case CATALOG_ITEM -> catalog.findById(targetId).filter(i->i.getDeletedAt()==null&&i.getListingStatus()==ListingStatus.ACTIVE&&visible(i.getModerationStatus())).map(i->merchants.findByIdAndDeletedAtIsNull(i.getMerchantProfileId()).map(this::visibleMerchant).orElse(false)).orElse(false);
            case REVIEW -> reviews.findById(targetId).map(r->r.getDeletedAt()==null&&r.getReviewStatus()==ReviewStatus.ACTIVE).orElse(false);
            case NEED_REQUEST -> contextMerchantId != null && canViewNeed(contextMerchantId,targetId);
            case USER -> false;
        };
    }
    private boolean canViewNeed(UUID merchantId,UUID needId){try{return needs.canViewNeed(merchantId,needId);}catch(RuntimeException ex){return false;}}
    private boolean visibleMerchant(vn.covie.merchants.model.MerchantProfileEntity merchant){return merchant.getMerchantStatus()==MerchantStatus.ACTIVE&&(merchant.getModerationStatus()==MerchantModerationStatus.NOT_REQUIRED||merchant.getModerationStatus()==MerchantModerationStatus.APPROVED);}
    private static boolean visible(CatalogModerationStatus status){return status==CatalogModerationStatus.NOT_REQUIRED||status==CatalogModerationStatus.APPROVED;}
}
