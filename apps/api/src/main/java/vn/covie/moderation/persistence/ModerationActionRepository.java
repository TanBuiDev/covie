package vn.covie.moderation.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.moderation.model.ModerationActionEntity;

public interface ModerationActionRepository extends JpaRepository<ModerationActionEntity, UUID> {
    List<ModerationActionEntity> findAllByModerationCaseIdOrderByCreatedAtAscIdAsc(UUID moderationCaseId);
}
