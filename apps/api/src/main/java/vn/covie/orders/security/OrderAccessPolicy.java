package vn.covie.orders.security;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.orders.model.OrderEntity;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class OrderAccessPolicy {
    private final CurrentActorProvider actors;
    private final MerchantAccessPolicy merchantAccess;

    public OrderAccessPolicy(CurrentActorProvider actors, MerchantAccessPolicy merchantAccess) {
        this.actors = actors;
        this.merchantAccess = merchantAccess;
    }

    public OrderAccess requireVisible(OrderEntity order) {
        CurrentActor actor = actors.currentActor();
        if (actor.userId().equals(order.getBuyerUserId())) return access(actor.userId(), OrderActorType.BUYER, order);
        if (actor.globalRoles().contains(SystemRole.ADMIN)) return access(actor.userId(), OrderActorType.ADMIN, order);
        try {
            merchantAccess.requireLocation(order.getMerchantProfileId(), order.getMerchantLocationId(), MerchantCapability.VIEW_ORDERS);
            return access(actor.userId(), OrderActorType.MERCHANT, order);
        } catch (ApiException exception) {
            if (exception.status() == HttpStatus.FORBIDDEN || exception.status() == HttpStatus.NOT_FOUND) throw notFound();
            throw exception;
        }
    }

    public OrderAccess requireAdminVisible(OrderEntity order) {
        CurrentActor actor = actors.currentActor();
        if (!actor.globalRoles().contains(SystemRole.ADMIN)) throw new ApiException(
                ErrorCode.ADMIN_ROLE_REQUIRED, HttpStatus.FORBIDDEN, "Administrator role is required.");
        return access(actor.userId(), OrderActorType.ADMIN, order);
    }

    public OrderAccess requireBuyer(OrderEntity order) {
        OrderAccess access = requireVisible(order);
        if (access.actorType() != OrderActorType.BUYER) throw actionNotAllowed();
        return access;
    }

    public OrderAccess requireMerchantProcessor(OrderEntity order) {
        OrderAccess access = requireVisible(order);
        if (access.actorType() != OrderActorType.MERCHANT) throw actionNotAllowed();
        merchantAccess.requireLocation(order.getMerchantProfileId(), order.getMerchantLocationId(), MerchantCapability.PROCESS_ORDERS);
        return access;
    }

    private static OrderAccess access(UUID userId, OrderActorType type, OrderEntity order) {
        return new OrderAccess(userId, type, order.getId(), order.getMerchantProfileId(), order.getMerchantLocationId());
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }

    private static ApiException actionNotAllowed() {
        return new ApiException(ErrorCode.ORDER_ACTION_NOT_ALLOWED, HttpStatus.FORBIDDEN, "Order action is not allowed.");
    }
}
