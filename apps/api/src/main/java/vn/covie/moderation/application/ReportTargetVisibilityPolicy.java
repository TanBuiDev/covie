package vn.covie.moderation.application;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
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
    private final JdbcTemplate jdbc; private final MerchantProfileRepository merchants; private final CatalogItemRepository catalog; private final ReviewRepository reviews; private final NeedMatchingService needs;
    public ReportTargetVisibilityPolicy(JdbcTemplate jdbc,MerchantProfileRepository merchants,CatalogItemRepository catalog,ReviewRepository reviews,NeedMatchingService needs){this.jdbc=jdbc;this.merchants=merchants;this.catalog=catalog;this.reviews=reviews;this.needs=needs;}
    public boolean isVisible(UUID actorId, ModerationTargetType type, UUID targetId, UUID contextMerchantId) {
        return switch(type) {
            case MERCHANT -> merchants.findByIdAndDeletedAtIsNull(targetId).map(this::visibleMerchant).orElse(false);
            case CATALOG_ITEM -> catalog.findById(targetId).filter(i->i.getDeletedAt()==null&&i.getListingStatus()==ListingStatus.ACTIVE&&visible(i.getModerationStatus())).map(i->merchants.findByIdAndDeletedAtIsNull(i.getMerchantProfileId()).map(this::visibleMerchant).orElse(false)).orElse(false);
            case REVIEW -> reviews.findById(targetId).map(r->r.getDeletedAt()==null&&r.getReviewStatus()==ReviewStatus.ACTIVE).orElse(false);
            case NEED_REQUEST -> contextMerchantId != null && canViewNeed(contextMerchantId,targetId);
            case SOCIAL_COMMENT -> visibleSocialComment(targetId);
            case USER -> false;
        };
    }
    private boolean visibleSocialComment(UUID id) {
        Integer found = jdbc.queryForObject("""
            select count(*) from catalog_social_comments c
            join catalog_items i on i.id=c.catalog_item_id
            join merchant_profiles m on m.id=i.merchant_profile_id
            where c.id=? and c.status='ACTIVE'
              and i.deleted_at is null and i.listing_status='ACTIVE'
              and i.moderation_status in ('NOT_REQUIRED','APPROVED')
              and m.deleted_at is null and m.merchant_status='ACTIVE'
              and m.moderation_status in ('NOT_REQUIRED','APPROVED')
            """,Integer.class,id);
        return found != null && found > 0;
    }
    private boolean canViewNeed(UUID merchantId,UUID needId){try{return needs.canViewNeed(merchantId,needId);}catch(RuntimeException ex){return false;}}
    private boolean visibleMerchant(vn.covie.merchants.model.MerchantProfileEntity merchant){return merchant.getMerchantStatus()==MerchantStatus.ACTIVE&&(merchant.getModerationStatus()==MerchantModerationStatus.NOT_REQUIRED||merchant.getModerationStatus()==MerchantModerationStatus.APPROVED);}
    private static boolean visible(CatalogModerationStatus status){return status==CatalogModerationStatus.NOT_REQUIRED||status==CatalogModerationStatus.APPROVED;}
}
