package vn.covie.media.application;

import java.io.InputStream;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CatalogVideoContentService {
    private final CatalogVideoUploadPolicy videos;
    private final MediaAssetRepository assets;
    private final JdbcTemplate jdbc;
    private final MerchantAccessPolicy access;
    private final ObjectStorage storage;

    public CatalogVideoContentService(CatalogVideoUploadPolicy videos, MediaAssetRepository assets,
            JdbcTemplate jdbc, MerchantAccessPolicy access, ObjectStorage storage) {
        this.videos = videos;
        this.assets = assets;
        this.jdbc = jdbc;
        this.access = access;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public MediaAssetEntity requireReadable(UUID id, CurrentActor actor) {
        videos.requireEnabled();
        var asset = assets.findByIdAndMediaStatusAndPurposeAndDeletedAtIsNull(id, MediaStatus.READY, MediaPurpose.CATALOG_VIDEO)
                .orElseThrow(CatalogVideoContentService::notFound);
        if (actor != null && (actor.userId().equals(asset.getOwnerUserId()) || actor.globalRoles().contains(SystemRole.ADMIN)))
            return asset;
        boolean visible = Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists (
                    select 1 from catalog_item_media cm
                    join catalog_items i on i.id=cm.catalog_item_id
                    join merchant_profiles m on m.id=i.merchant_profile_id
                    where cm.media_asset_id=? and cm.media_type='VIDEO'
                      and i.deleted_at is null and i.listing_status='ACTIVE'
                      and i.moderation_status in ('NOT_REQUIRED','APPROVED')
                      and m.deleted_at is null and m.merchant_status='ACTIVE'
                      and m.moderation_status in ('NOT_REQUIRED','APPROVED'))
                """, Boolean.class, id));
        if (visible) return asset;
        if (actor != null) {
            var merchantIds = jdbc.query("""
                    select distinct i.merchant_profile_id from catalog_item_media cm
                    join catalog_items i on i.id=cm.catalog_item_id
                    join merchant_profiles m on m.id=i.merchant_profile_id
                    join merchant_memberships membership on membership.merchant_profile_id=m.id
                    where cm.media_asset_id=? and cm.media_type='VIDEO' and i.deleted_at is null
                      and m.deleted_at is null and membership.user_id=? and membership.membership_status='ACTIVE'
                    """, (row, index) -> row.getObject(1, UUID.class), id, actor.userId());
            for (UUID merchantId : merchantIds) {
                try {
                    access.require(merchantId, MerchantCapability.VIEW_MERCHANT);
                    return asset;
                } catch (ApiException denied) {
                    if (denied.status() != HttpStatus.NOT_FOUND && denied.status() != HttpStatus.FORBIDDEN) throw denied;
                }
            }
        }
        throw notFound();
    }

    public InputStream openRange(MediaAssetEntity asset, long first, long last) {
        try {
            return storage.openRange(asset.getStorageKey(), first, last, asset.getSizeBytes());
        } catch (RuntimeException failure) {
            throw unavailable();
        }
    }

    public static ApiException unavailable() {
        return new ApiException(ErrorCode.MEDIA_UPLOAD_VERIFICATION_FAILED, HttpStatus.SERVICE_UNAVAILABLE,
                "Video content is unavailable. Retry later.");
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
