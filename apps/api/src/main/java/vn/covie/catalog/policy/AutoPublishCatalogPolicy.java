package vn.covie.catalog.policy;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import vn.covie.catalog.model.CatalogItemEntity;

@Component
@Primary
public final class AutoPublishCatalogPolicy implements CatalogPublishPolicy {
    @Override
    public CatalogPublishDecision decide(CatalogItemEntity item) {
        return CatalogPublishDecision.PUBLISH_NOW;
    }
}
