package vn.covie.orders.application;

import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.idempotency.IdempotencyExecutor;

/** Applies the Order-scoped idempotency contract to every lifecycle mutation. */
@Service
public class OrderCommandIdempotencyService {
    private final IdempotencyExecutor idempotency;
    private final CurrentActorProvider actors;

    public OrderCommandIdempotencyService(IdempotencyExecutor idempotency, CurrentActorProvider actors) {
        this.idempotency = idempotency;
        this.actors = actors;
    }

    public <T> T execute(UUID orderId, String command, String key, Object request, Class<T> responseType,
            Supplier<T> action) {
        return idempotency.execute(actors.currentActor().userId(), "order." + command + ":" + orderId,
                key, request, 200, responseType, action);
    }

    public void executeVoid(UUID orderId, String command, String key, Object request, Runnable action) {
        execute(orderId, command, key, request, Map.class, () -> {
            action.run();
            return Map.of("status", "OK");
        });
    }
}
