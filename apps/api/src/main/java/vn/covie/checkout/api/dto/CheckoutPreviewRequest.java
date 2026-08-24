package vn.covie.checkout.api.dto;

import java.util.List;
import java.util.UUID;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.shared.commerce.PaymentMethod;

/**
 * Checkout request. The nested source is the Plan 06 canonical contract; flattened
 * sourceType/cartId/need* fields remain accepted for backwards compatibility with
 * already-generated Plan 04 clients and stored checkout-session snapshots.
 */
public record CheckoutPreviewRequest(
        Source source,
        CheckoutSourceType sourceType,
        UUID cartId,
        UUID needRequestId,
        UUID needResponseItemId,
        List<DirectItem> items,
        FulfillmentMethod fulfillmentMethod,
        UUID addressId,
        PaymentMethod paymentMethod) {

    public CheckoutPreviewRequest {
        if (source != null) {
            sourceType = source.type();
            cartId = source.cartId();
            needRequestId = source.needRequestId();
            needResponseItemId = source.needResponseItemId();
        }
    }

    /** Existing Plan 04 Java call sites keep compiling. */
    public CheckoutPreviewRequest(CheckoutSourceType sourceType, UUID cartId, List<DirectItem> items,
            FulfillmentMethod fulfillmentMethod, UUID addressId, PaymentMethod paymentMethod) {
        this(null, sourceType, cartId, null, null, items, fulfillmentMethod, addressId, paymentMethod);
    }

    public static CheckoutPreviewRequest need(UUID needRequestId, UUID needResponseItemId,
            List<DirectItem> items, FulfillmentMethod fulfillmentMethod, UUID addressId, PaymentMethod paymentMethod) {
        Source source = new Source(CheckoutSourceType.NEED_REQUEST, null, needRequestId, needResponseItemId);
        return new CheckoutPreviewRequest(source, source.type(), null, needRequestId, needResponseItemId,
                items, fulfillmentMethod, addressId, paymentMethod);
    }

    public record Source(CheckoutSourceType type, UUID cartId, UUID needRequestId, UUID needResponseItemId) {}
    public record DirectItem(UUID locationVariantId, long quantity) {}
}
