package vn.covie.orders.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.orders.model.OrderActorContext;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderEventEntity;
import vn.covie.orders.model.OrderEventType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.persistence.OrderEventRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;

@Service
public class OrderEventService {
    private final OrderEventRepository events;
    private final UuidV7Generator ids;
    private final ObjectMapper mapper;
    private final AppClock clock;

    public OrderEventService(OrderEventRepository events, UuidV7Generator ids, ObjectMapper mapper, AppClock clock) {
        this.events = events; this.ids = ids; this.mapper = mapper; this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public OrderEventEntity append(OrderEntity order, OrderEventType eventType, UUID actorUserId,
            OrderActorContext actorContext, OrderStatus fromStatus, OrderStatus toStatus, String reasonCode,
            String note, Object metadata) {
        OrderEventEntity event = OrderEventEntity.lifecycle(ids.generate(), order.getId(), eventType, actorUserId,
                actorContext, fromStatus, toStatus, reasonCode, note, mapper.valueToTree(metadata), clock.now());
        return events.save(event);
    }
}
