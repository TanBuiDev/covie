package vn.covie.moderation.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.catalog.application.CatalogPublishValidator;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.moderation.model.ModerationActionType;
import vn.covie.moderation.model.ModerationTargetType;

@Component
public class CatalogModerationTargetHandler implements ModerationTargetHandler {
    private final CatalogItemRepository items;
    private final CatalogPublishValidator validator;

    public CatalogModerationTargetHandler(CatalogItemRepository items, CatalogPublishValidator validator) {
        this.items = items;
        this.validator = validator;
    }

    @Override public ModerationTargetType supports() { return ModerationTargetType.CATALOG_ITEM; }
    @Override @Transactional(readOnly = true) public ModerationTargetSnapshot inspect(UUID id) { return snapshot(item(id)); }
    @Override @Transactional public ModerationTargetMutationResult apply(UUID id, ModerationActionType action, Instant now) {
        CatalogItemEntity item = item(id);
        ModerationTargetSnapshot before = snapshot(item);
        switch (action) {
            case APPROVE -> { validator.requirePublishable(item); item.approveModeration(now); }
            case REJECT -> item.rejectModeration(now);
            case HIDE -> item.hide(now);
            case RESTORE -> item.restoreVisibility(now);
            default -> throw new IllegalArgumentException("Catalog action is not supported.");
        }
        return new ModerationTargetMutationResult(before, snapshot(item));
    }
    private CatalogItemEntity item(UUID id) { return items.findById(id).orElseThrow(); }
    private static ModerationTargetSnapshot snapshot(CatalogItemEntity item) {
        return new ModerationTargetSnapshot(ModerationTargetType.CATALOG_ITEM, item.getId(), item.getListingStatus().name(), item.getModerationStatus().name());
    }
}
