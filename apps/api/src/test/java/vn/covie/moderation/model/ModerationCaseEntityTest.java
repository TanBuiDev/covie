package vn.covie.moderation.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ModerationCaseEntityTest {

    @Test
    void preApprovalCatalogStartsOpenAndResolves() {
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        UUID catalogItemId = UUID.randomUUID();
        ModerationCaseEntity moderationCase = ModerationCaseEntity.preApprovalCatalog(UUID.randomUUID(), catalogItemId, now);

        assertThat(moderationCase.isOpen()).isTrue();
        assertThat(moderationCase.getTargetType()).isEqualTo(ModerationTargetType.CATALOG_ITEM);
        assertThat(moderationCase.getTargetCatalogItemId()).isEqualTo(catalogItemId);

        moderationCase.resolve(now.plusSeconds(1));

        assertThat(moderationCase.getCaseStatus()).isEqualTo(ModerationCaseStatus.RESOLVED);
        assertThat(moderationCase.getResolvedAt()).isEqualTo(now.plusSeconds(1));
    }
}
