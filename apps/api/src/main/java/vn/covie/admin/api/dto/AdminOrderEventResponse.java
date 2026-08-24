package vn.covie.admin.api.dto;
import java.time.Instant; import java.util.UUID; import vn.covie.orders.model.*;
public record AdminOrderEventResponse(UUID id,OrderEventType type,UUID actorUserId,OrderActorContext actorContext,OrderStatus fromStatus,OrderStatus toStatus,String reasonCode,Instant createdAt) {}
