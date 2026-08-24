package vn.covie.notifications.persistence;
import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository; import vn.covie.notifications.model.NotificationDeliveryEntity;
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDeliveryEntity,UUID> {}
