package vn.covie.notifications.persistence;
import java.time.Instant; import java.util.List; import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import vn.covie.notifications.model.NotificationEntity; import vn.covie.notifications.model.NotificationType;
public interface NotificationRepository extends JpaRepository<NotificationEntity,UUID> { Optional<NotificationEntity> findBySourceOutboxEventIdAndUserIdAndNotificationType(UUID source, UUID userId, NotificationType type);
 List<NotificationEntity> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId, org.springframework.data.domain.Pageable page);
 @Query("select n from NotificationEntity n where n.userId=:userId and (n.createdAt < :createdAt or (n.createdAt=:createdAt and n.id < :id)) order by n.createdAt desc,n.id desc") List<NotificationEntity> pageAfter(@Param("userId") UUID userId,@Param("createdAt") Instant createdAt,@Param("id") UUID id,org.springframework.data.domain.Pageable page);
 Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);
 @Modifying @Query("update NotificationEntity n set n.readAt=coalesce(n.readAt,:now) where n.id=:id and n.userId=:userId") int markRead(@Param("id") UUID id,@Param("userId") UUID userId,@Param("now") Instant now);
 @Modifying @Query("update NotificationEntity n set n.readAt=:now where n.userId=:userId and n.readAt is null") int markAllRead(@Param("userId") UUID userId,@Param("now") Instant now);
}
