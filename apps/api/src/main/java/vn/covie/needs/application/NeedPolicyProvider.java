package vn.covie.needs.application;

import java.util.UUID;

public interface NeedPolicyProvider {
    NeedPolicySnapshot resolve(UUID merchantId, UUID categoryId, UUID locationAreaId);
}
