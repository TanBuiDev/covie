package vn.covie.notifications.persistence;
import java.util.Optional; import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository; import vn.covie.notifications.model.NotificationEntity; import vn.covie.notifications.model.NotificationType;
public interface NotificationRepository extends JpaRepository<NotificationEntity,UUID> { Optional<NotificationEntity> findBySourceOutboxEventIdAndUserIdAndNotificationType(UUID source, UUID userId, NotificationType type); }
