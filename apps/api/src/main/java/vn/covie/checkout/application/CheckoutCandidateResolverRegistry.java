package vn.covie.checkout.application;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public class CheckoutCandidateResolverRegistry {
    private final Map<CheckoutSourceType, CheckoutCandidateResolver> resolvers;

    public CheckoutCandidateResolverRegistry(List<CheckoutCandidateResolver> values) {
        Map<CheckoutSourceType, CheckoutCandidateResolver> map = new EnumMap<>(CheckoutSourceType.class);
        for (var value : values) {
            if (map.put(value.supports(), value) != null) throw new IllegalStateException("Duplicate checkout resolver");
        }
        resolvers = Map.copyOf(map);
    }

    public CheckoutCandidate resolve(UUID user, CheckoutPreviewRequest request) {
        return resolver(request).resolve(user, request);
    }

    public void lockSourceForCommit(UUID user, CheckoutPreviewRequest request) {
        resolver(request).lockSourceForCommit(user, request);
    }

    private CheckoutCandidateResolver resolver(CheckoutPreviewRequest request) {
        if (request == null || request.sourceType() == null) {
            throw new ApiException(ErrorCode.CHECKOUT_SOURCE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Checkout source is required.");
        }
        var resolver = resolvers.get(request.sourceType());
        if (resolver == null) {
            throw new ApiException(ErrorCode.CHECKOUT_SOURCE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Unsupported checkout source.");
        }
        return resolver;
    }
}
