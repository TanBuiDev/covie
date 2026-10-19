package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.catalog.model.*;

class CatalogItemModerationStateTest {
    @Test void approvalRetainsTheExistingDraftAndActivePublicationBehavior() {
        Instant now=Instant.now();
        for (boolean alreadyActive : new boolean[]{false,true}) {
            var item=CatalogItemEntity.create(UUID.randomUUID(),UUID.randomUUID(),CatalogItemType.PRODUCT,
                    ListingKind.STANDARD,UUID.randomUUID(),"Item","item",null,now);
            if (alreadyActive) item.publish(now);
            item.requireModeration(now);
            item.approveModeration(now);
            assertThat(item.getModerationStatus()).isEqualTo(CatalogModerationStatus.APPROVED);
            assertThat(item.getListingStatus()).isEqualTo(ListingStatus.ACTIVE);
        }
    }
}
