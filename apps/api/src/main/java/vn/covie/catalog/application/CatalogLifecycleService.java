package vn.covie.catalog.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.policy.CatalogPublishDecision;
import vn.covie.catalog.policy.CatalogPublishPolicy;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.moderation.model.ModerationTargetType;
import vn.covie.shared.moderation.ModerationCaseWriter;
import vn.covie.shared.moderation.ModerationTargetRef;
import vn.covie.shared.outbox.OutboxEventIntent;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CatalogLifecycleService {
    private final MerchantAccessPolicy access;
    private final CatalogItemRepository items;
    private final CatalogPublishValidator validator;
    private final CatalogPublishPolicy policy;
    private final OutboxEventWriter outbox;
    private final ModerationCaseWriter moderationCases;
    private final AppClock clock;

    public CatalogLifecycleService(MerchantAccessPolicy access, CatalogItemRepository items, CatalogPublishValidator validator,
            CatalogPublishPolicy policy, OutboxEventWriter outbox, ModerationCaseWriter moderationCases, AppClock clock) {
        this.access = access; this.items = items; this.validator = validator; this.policy = policy; this.outbox = outbox; this.moderationCases = moderationCases; this.clock = clock;
    }

    @Transactional
    public LifecycleResult publish(UUID merchantId, UUID itemId, String ifMatch) {
        access.require(merchantId, MerchantCapability.PUBLISH_CATALOG);
        CatalogItemEntity item = locked(merchantId, itemId, ifMatch);
        requireState(item, ListingStatus.DRAFT, "Only draft catalog items can be published.");
        validator.requirePublishable(item);
        Instant now = clock.now();
        if (policy.decide(item) == CatalogPublishDecision.REQUIRE_MODERATION) {
            item.requireModeration(now);
            moderationCases.openPreApproval(new ModerationTargetRef(ModerationTargetType.CATALOG_ITEM, item.getId()),
                    "PUBLISH_REQUIRES_MODERATION");
            return saved(item);
        }
        item.publish(now);
        LifecycleResult result = saved(item);
        append("CATALOG_ITEM_PUBLISHED", item);
        return result;
    }

    @Transactional
    public LifecycleResult pause(UUID merchantId, UUID itemId, String ifMatch) {
        access.require(merchantId, MerchantCapability.PUBLISH_CATALOG);
        CatalogItemEntity item = locked(merchantId, itemId, ifMatch);
        requireState(item, ListingStatus.ACTIVE, "Only active catalog items can be paused.");
        item.pause(clock.now());
        LifecycleResult result = saved(item);
        append("CATALOG_ITEM_PAUSED", item);
        return result;
    }

    @Transactional
    public LifecycleResult activate(UUID merchantId, UUID itemId, String ifMatch) {
        access.require(merchantId, MerchantCapability.PUBLISH_CATALOG);
        CatalogItemEntity item = locked(merchantId, itemId, ifMatch);
        requireState(item, ListingStatus.PAUSED, "Only paused catalog items can be activated.");
        if (!isVisible(item.getModerationStatus()))
            throw lifecycleInvalid("Catalog item is not visible after moderation.");
        validator.requirePublishable(item);
        item.activate(clock.now());
        LifecycleResult result = saved(item);
        append("CATALOG_ITEM_ACTIVATED", item);
        return result;
    }

    @Transactional
    public LifecycleResult archive(UUID merchantId, UUID itemId, String ifMatch) {
        access.require(merchantId, MerchantCapability.PUBLISH_CATALOG);
        CatalogItemEntity item = locked(merchantId, itemId, ifMatch);
        if (item.getListingStatus() == ListingStatus.DELETED) throw lifecycleInvalid("Deleted catalog items are terminal.");
        item.delete(clock.now());
        LifecycleResult result = saved(item);
        append("CATALOG_ITEM_ARCHIVED", item);
        return result;
    }

    private CatalogItemEntity locked(UUID merchantId, UUID itemId, String ifMatch) {
        CatalogItemEntity item = items.findLockedByIdAndMerchantProfileIdAndDeletedAtIsNull(itemId, merchantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Catalog item was not found."));
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), item.getVersion());
        return item;
    }
    private LifecycleResult saved(CatalogItemEntity item) {
        CatalogItemEntity saved = items.saveAndFlush(item);
        return new LifecycleResult(saved.getId(), saved.getVersion());
    }
    private void append(String type, CatalogItemEntity item) {
        outbox.append(new OutboxEventIntent(type, "CATALOG_ITEM", item.getId(), Map.of("merchantId", item.getMerchantProfileId().toString())));
    }
    private static void requireState(CatalogItemEntity item, ListingStatus expected, String message) {
        if (item.getListingStatus() != expected) throw lifecycleInvalid(message);
    }
    private static boolean isVisible(CatalogModerationStatus status) {
        return status == CatalogModerationStatus.NOT_REQUIRED || status == CatalogModerationStatus.APPROVED;
    }
    private static ApiException lifecycleInvalid(String message) {
        return new ApiException(ErrorCode.CATALOG_LIFECYCLE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
    public record LifecycleResult(UUID id, long version) {}
}
