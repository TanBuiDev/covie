package vn.covie.orders.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.orders.model.OrderEntity;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    boolean existsByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> lockById(@Param("id") UUID id);

    List<OrderEntity> findAllByBuyerUserIdOrderByCreatedAtDesc(UUID buyerUserId);
    List<OrderEntity> findAllByMerchantProfileIdOrderByCreatedAtDesc(UUID merchantProfileId);
    @Query("select o from OrderEntity o where o.buyerUserId=:buyerId and (:createdAt is null or o.createdAt < :createdAt or (o.createdAt=:createdAt and o.id < :id)) order by o.createdAt desc,o.id desc")
    List<OrderEntity> findBuyerPage(@Param("buyerId") UUID buyerId,@Param("createdAt") java.time.Instant createdAt,@Param("id") UUID id,org.springframework.data.domain.Pageable page);
    @Query("select o from OrderEntity o where o.merchantProfileId=:merchantId and (:locationId is null or o.merchantLocationId=:locationId) and (:createdAt is null or o.createdAt < :createdAt or (o.createdAt=:createdAt and o.id < :id)) order by o.createdAt desc,o.id desc")
    List<OrderEntity> findMerchantPage(@Param("merchantId") UUID merchantId,@Param("locationId") UUID locationId,@Param("createdAt") java.time.Instant createdAt,@Param("id") UUID id,org.springframework.data.domain.Pageable page);
    List<OrderEntity> findTop100ByStatusAndConfirmationExpiresAtLessThanEqualOrderByConfirmationExpiresAtAsc(
            vn.covie.orders.model.OrderStatus status, java.time.Instant now);

    @Query("select o.id from OrderEntity o, ProductFulfillmentEntity f where f.orderId=o.id and o.status=:status and f.autoCompleteAt <= :now order by f.autoCompleteAt asc")
    List<UUID> findDueProductAutoCompletionIds(@Param("status") vn.covie.orders.model.OrderStatus status, @Param("now") java.time.Instant now, org.springframework.data.domain.Pageable page);

    @Query("select o.id from OrderEntity o, ServiceFulfillmentEntity f where f.orderId=o.id and o.status=:status and f.autoCompleteAt <= :now order by f.autoCompleteAt asc")
    List<UUID> findDueServiceAutoCompletionIds(@Param("status") vn.covie.orders.model.OrderStatus status, @Param("now") java.time.Instant now, org.springframework.data.domain.Pageable page);
}
