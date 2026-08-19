package vn.covie.catalog.policy;

import vn.covie.catalog.model.CatalogItemEntity;

public interface CatalogPublishPolicy {
    CatalogPublishDecision decide(CatalogItemEntity item);
}
