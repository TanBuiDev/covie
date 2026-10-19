package vn.covie.needs.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.needs.model.NeedResponseItemEntity;
import vn.covie.needs.model.NeedResponseItemStatus;

public interface NeedResponseItemRepository extends JpaRepository<NeedResponseItemEntity, UUID> {
    List<NeedResponseItemEntity> findByNeedResponseIdAndItemStatusOrderByCreatedAtAsc(
            UUID responseId, NeedResponseItemStatus itemStatus);
    List<NeedResponseItemEntity> findByNeedResponseIdInAndItemStatusOrderByCreatedAtAsc(
            java.util.Collection<UUID> responseIds, NeedResponseItemStatus itemStatus);
}
