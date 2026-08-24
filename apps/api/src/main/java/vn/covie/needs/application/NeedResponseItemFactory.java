package vn.covie.needs.application;

import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.covie.needs.model.NeedResponseItemEntity;
import vn.covie.shared.id.UuidV7Generator;

@Component
public class NeedResponseItemFactory {
    private final UuidV7Generator ids;

    public NeedResponseItemFactory(UuidV7Generator ids) {
        this.ids = ids;
    }

    public NeedResponseItemEntity active(UUID responseId, EligibleNeedResponseItem item, Instant now) {
        return NeedResponseItemEntity.active(ids.generate(), responseId, item.catalogItemId(), item.variantId(),
                item.locationVariantId(), item.currentPrice(), item.currency(), item.availabilitySnapshot(), now);
    }
}
