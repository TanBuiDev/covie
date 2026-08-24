package vn.covie.reputation.application;

import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.persistence.OrderRepository;

@Service
public class ReputationRebuildService {
    static final long MAINTENANCE_LOCK = 0x434F564945525042L;
    private final OrderRepository orders; private final ReputationProjectionService projection;
    private final ReputationPolicyProvider policy; private final EntityManager entityManager;
    public ReputationRebuildService(OrderRepository orders, ReputationProjectionService projection,
            ReputationPolicyProvider policy, EntityManager entityManager) {
        this.orders=orders; this.projection=projection; this.policy=policy; this.entityManager=entityManager;
    }
    @Transactional
    public void rebuildOrder(UUID orderId) {
        projection.projectCurrentOrderWithoutOutbox(orders.lockById(orderId).orElseThrow());
    }
    @Transactional
    public void rebuildAllForMaintenance() {
        if (!policy.allowMaintenanceRebuild()) throw new IllegalStateException("Maintenance reputation rebuild is disabled.");
        entityManager.createNativeQuery("select pg_advisory_xact_lock(:lock)").setParameter("lock", MAINTENANCE_LOCK).getSingleResult();
        entityManager.createNativeQuery("truncate table reputation_order_outcomes, user_reputation_stats, merchant_reputation_stats").executeUpdate();
        entityManager.flush();
        UUID after=null;
        while (true) {
            List<OrderEntity> page=after == null ? orders.findTop500ByOrderByIdAsc() : orders.findTop500ByIdGreaterThanOrderByIdAsc(after);
            if (page.isEmpty()) return;
            for (OrderEntity order : page) projection.projectCurrentOrderWithoutOutbox(order);
            entityManager.flush(); entityManager.clear();
            after=page.getLast().getId();
        }
    }
}
