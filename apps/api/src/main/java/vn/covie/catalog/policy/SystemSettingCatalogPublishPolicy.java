package vn.covie.catalog.policy;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.moderation.application.ModerationDecision;
import vn.covie.moderation.application.ModerationDecisionService;
import vn.covie.moderation.application.ModerationSubject;
import vn.covie.moderation.model.ModerationTargetType;

@Component
@Primary
public final class SystemSettingCatalogPublishPolicy implements CatalogPublishPolicy {
    private final ModerationDecisionService moderation;
    public SystemSettingCatalogPublishPolicy(ModerationDecisionService moderation) { this.moderation = moderation; }
    @Override public CatalogPublishDecision decide(CatalogItemEntity item) {
        ModerationDecision decision = moderation.decide(new ModerationSubject(ModerationTargetType.CATALOG_ITEM, null, item.getMerchantProfileId(), item.getPrimaryCategoryId(), null));
        return decision == ModerationDecision.PUBLISH_NOW ? CatalogPublishDecision.PUBLISH_NOW : CatalogPublishDecision.REQUIRE_MODERATION;
    }
}
