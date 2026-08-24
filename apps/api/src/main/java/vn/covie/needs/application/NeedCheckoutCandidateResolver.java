package vn.covie.needs.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.persistence.CatalogItemRepository;
import vn.covie.catalog.persistence.LocationVariantRepository;
import vn.covie.catalog.persistence.VariantRepository;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.application.CheckoutCandidate;
import vn.covie.checkout.application.CheckoutCandidateItem;
import vn.covie.checkout.application.CheckoutCandidateResolver;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedResponseEntity;
import vn.covie.needs.model.NeedResponseItemEntity;
import vn.covie.needs.model.NeedResponseItemStatus;
import vn.covie.needs.model.NeedResponseStatus;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.needs.persistence.NeedResponseItemRepository;
import vn.covie.needs.persistence.NeedResponseRepository;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Component
public class NeedCheckoutCandidateResolver implements CheckoutCandidateResolver {
    private final NeedRequestRepository needs;
    private final NeedResponseRepository responses;
    private final NeedResponseItemRepository responseItems;
    private final LocationVariantRepository locationVariants;
    private final VariantRepository variants;
    private final CatalogItemRepository catalogItems;
    private final ObjectMapper mapper;
    private final AppClock clock;

    public NeedCheckoutCandidateResolver(NeedRequestRepository needs, NeedResponseRepository responses,
            NeedResponseItemRepository responseItems, LocationVariantRepository locationVariants,
            VariantRepository variants, CatalogItemRepository catalogItems, ObjectMapper mapper, AppClock clock) {
        this.needs = needs; this.responses = responses; this.responseItems = responseItems;
        this.locationVariants = locationVariants; this.variants = variants; this.catalogItems = catalogItems;
        this.mapper = mapper; this.clock = clock;
    }

    @Override public CheckoutSourceType supports() { return CheckoutSourceType.NEED_REQUEST; }

    @Override
    public CheckoutCandidate resolve(UUID buyerUserId, CheckoutPreviewRequest request) {
        SourceChain source = readSource(buyerUserId, request, false);
        CheckoutPreviewRequest.DirectItem requestedItem = requireSingleItem(request);
        if (!source.item().getLocationVariantId().equals(requestedItem.locationVariantId())) {
            throw invalid("Checkout item does not match the selected Need response item.");
        }
        if (requestedItem.quantity() < 1) throw invalid("Checkout quantity must be positive.");
        if (source.need().getNeedType() == NeedType.SERVICE && requestedItem.quantity() != 1) {
            throw invalid("Service quantity must be one.");
        }

        var offer = locationVariants.findUsableById(source.item().getLocationVariantId())
                .orElseThrow(() -> invalid("Need response offer is unavailable."));
        if (!offer.getMerchantLocationId().equals(source.response().getMerchantLocationId())) {
            throw invalid("Need response source location changed.");
        }
        var variant = variants.findById(offer.getVariantId())
                .filter(v -> v.getId().equals(source.item().getVariantId()))
                .orElseThrow(() -> invalid("Need response variant changed."));
        var catalog = catalogItems.findById(variant.getCatalogItemId())
                .filter(v -> v.getId().equals(source.item().getCatalogItemId()))
                .orElseThrow(() -> invalid("Need response catalog item changed."));
        CatalogItemType expected = source.need().getNeedType() == NeedType.PRODUCT
                ? CatalogItemType.PRODUCT : CatalogItemType.SERVICE;
        if (catalog.getItemType() != expected) throw invalid("Need response type changed.");

        NeedSourceContext context = new NeedSourceContext(source.need().getId(), source.response().getId(),
                source.item().getId(), source.response().getVersion());
        return new CheckoutCandidate(CheckoutSourceType.NEED_REQUEST, buyerUserId,
                source.response().getMerchantProfileId(), source.response().getMerchantLocationId(), expected,
                List.of(new CheckoutCandidateItem(offer.getId(), variant.getId(), catalog.getId(), requestedItem.quantity())),
                null, mapper.valueToTree(context));
    }

    @Override
    public void lockSourceForCommit(UUID buyerUserId, CheckoutPreviewRequest request) {
        readSource(buyerUserId, request, true);
    }

    private SourceChain readSource(UUID buyerUserId, CheckoutPreviewRequest request, boolean lock) {
        if (request.needRequestId() == null || request.needResponseItemId() == null) {
            throw invalid("Need source identifiers are required.");
        }
        NeedRequestEntity need = (lock ? needs.lockById(request.needRequestId()) : needs.findById(request.needRequestId()))
                .filter(v -> buyerUserId.equals(v.getBuyerUserId()))
                .orElseThrow(() -> invalidOrChanged(lock, "Need source is unavailable."));
        Instant now = clock.now();
        if (!need.isActiveAt(now)) throw invalidOrChanged(lock, "Need source is no longer active.");

        NeedResponseItemEntity item = responseItems.findById(request.needResponseItemId())
                .orElseThrow(() -> invalidOrChanged(lock, "Need response item is unavailable."));
        NeedResponseEntity response = (lock
                ? responses.lockByNeedAndId(need.getId(), item.getNeedResponseId())
                : responses.findById(item.getNeedResponseId()).filter(v -> need.getId().equals(v.getNeedRequestId())))
                .orElseThrow(() -> invalidOrChanged(lock, "Need response is unavailable."));
        if (response.getResponseStatus() != NeedResponseStatus.ACTIVE
                || item.getItemStatus() != NeedResponseItemStatus.ACTIVE
                || !item.getNeedResponseId().equals(response.getId())) {
            throw invalidOrChanged(lock, "Need response source is no longer active.");
        }
        return new SourceChain(need, response, item);
    }

    private static CheckoutPreviewRequest.DirectItem requireSingleItem(CheckoutPreviewRequest request) {
        if (request.items() == null || request.items().size() != 1 || request.items().getFirst() == null) {
            throw invalid("Need checkout requires exactly one item.");
        }
        return request.items().getFirst();
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.NEED_SOURCE_INVALID, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }

    private static ApiException invalidOrChanged(boolean commit, String message) {
        return commit
                ? new ApiException(ErrorCode.CHECKOUT_CHANGED, HttpStatus.CONFLICT, message)
                : invalid(message);
    }

    private record SourceChain(NeedRequestEntity need, NeedResponseEntity response, NeedResponseItemEntity item) {}
}
