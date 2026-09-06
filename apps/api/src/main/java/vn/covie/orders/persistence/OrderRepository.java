package vn.covie.orders.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import vn.covie.orders.model.OrderEntity;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    boolean existsByOrderCode(String orderCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from OrderEntity o where o.id = :id")
    Optional<OrderEntity> lockById(@Param("id") UUID id);

    List<OrderEntity> findAllByBuyerUserIdOrderByCreatedAtDesc(UUID buyerUserId);
    List<OrderEntity> findAllByMerchantProfileIdOrderByCreatedAtDesc(UUID merchantProfileId);
    default List<OrderEntity> findBuyerPage(UUID buyerId, java.time.Instant createdAt, UUID id, Pageable page) {
        return createdAt == null || id == null
                ? findBuyerFirstPage(buyerId, page)
                : findBuyerPageAfter(buyerId, createdAt, id, page);
    }
    @Query("select o from OrderEntity o where o.buyerUserId=:buyerId order by o.createdAt desc,o.id desc")
    List<OrderEntity> findBuyerFirstPage(@Param("buyerId") UUID buyerId, Pageable page);
    @Query("select o from OrderEntity o where o.buyerUserId=:buyerId and (o.createdAt < :createdAt or (o.createdAt=:createdAt and o.id < :id)) order by o.createdAt desc,o.id desc")
    List<OrderEntity> findBuyerPageAfter(@Param("buyerId") UUID buyerId,@Param("createdAt") java.time.Instant createdAt,@Param("id") UUID id,Pageable page);

    default List<OrderEntity> findMerchantPage(UUID merchantId, UUID locationId, java.time.Instant createdAt, UUID id, Pageable page) {
        if (createdAt == null || id == null) {
            return locationId == null
                    ? findMerchantFirstPage(merchantId, page)
                    : findMerchantFirstPageAtLocation(merchantId, locationId, page);
        }
        return locationId == null
                ? findMerchantPageAfter(merchantId, createdAt, id, page)
                : findMerchantPageAfterAtLocation(merchantId, locationId, createdAt, id, page);
    }
    @Query("select o from OrderEntity o where o.merchantProfileId=:merchantId order by o.createdAt desc,o.id desc")
    List<OrderEntity> findMerchantFirstPage(@Param("merchantId") UUID merchantId, Pageable page);
    @Query("select o from OrderEntity o where o.merchantProfileId=:merchantId and o.merchantLocationId=:locationId order by o.createdAt desc,o.id desc")
    List<OrderEntity> findMerchantFirstPageAtLocation(@Param("merchantId") UUID merchantId,@Param("locationId") UUID locationId,Pageable page);
    @Query("select o from OrderEntity o where o.merchantProfileId=:merchantId and (o.createdAt < :createdAt or (o.createdAt=:createdAt and o.id < :id)) order by o.createdAt desc,o.id desc")
    List<OrderEntity> findMerchantPageAfter(@Param("merchantId") UUID merchantId,@Param("createdAt") java.time.Instant createdAt,@Param("id") UUID id,Pageable page);
    @Query("select o from OrderEntity o where o.merchantProfileId=:merchantId and o.merchantLocationId=:locationId and (o.createdAt < :createdAt or (o.createdAt=:createdAt and o.id < :id)) order by o.createdAt desc,o.id desc")
    List<OrderEntity> findMerchantPageAfterAtLocation(@Param("merchantId") UUID merchantId,@Param("locationId") UUID locationId,@Param("createdAt") java.time.Instant createdAt,@Param("id") UUID id,Pageable page);
    List<OrderEntity> findTop500ByOrderByIdAsc();
    List<OrderEntity> findTop500ByIdGreaterThanOrderByIdAsc(UUID afterId);
    List<OrderEntity> findTop100ByStatusAndConfirmationExpiresAtLessThanEqualOrderByConfirmationExpiresAtAsc(
            vn.covie.orders.model.OrderStatus status, java.time.Instant now);

    @Query("select o.id from OrderEntity o, ProductFulfillmentEntity f where f.orderId=o.id and o.status=:status and f.autoCompleteAt <= :now order by f.autoCompleteAt asc")
    List<UUID> findDueProductAutoCompletionIds(@Param("status") vn.covie.orders.model.OrderStatus status, @Param("now") java.time.Instant now, org.springframework.data.domain.Pageable page);

    @Query("select o.id from OrderEntity o, ServiceFulfillmentEntity f where f.orderId=o.id and o.status=:status and f.autoCompleteAt <= :now order by f.autoCompleteAt asc")
    List<UUID> findDueServiceAutoCompletionIds(@Param("status") vn.covie.orders.model.OrderStatus status, @Param("now") java.time.Instant now, org.springframework.data.domain.Pageable page);
    @Query("select o from OrderEntity o where (:status is null or o.status = :status) and (:type is null or o.orderType = :type) and (:merchantId is null or o.merchantProfileId = :merchantId) and (:buyerUserId is null or o.buyerUserId = :buyerUserId) and (cast(:orderCode as string) is null or lower(o.orderCode) like concat('%', lower(cast(:orderCode as string)), '%'))")
    Page<OrderEntity> searchAdmin(@Param("status") vn.covie.orders.model.OrderStatus status, @Param("type") vn.covie.orders.model.OrderType type, @Param("merchantId") UUID merchantId, @Param("buyerUserId") UUID buyerUserId, @Param("orderCode") String orderCode, Pageable pageable);
}
