package vn.covie.moderation.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.moderation.model.ModerationCaseEntity;
import vn.covie.moderation.model.ModerationCaseStatus;
import vn.covie.moderation.model.ModerationCaseType;
import vn.covie.moderation.model.ModerationTargetType;

public interface ModerationCaseRepository extends JpaRepository<ModerationCaseEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ModerationCaseEntity c where c.id=:id")
    Optional<ModerationCaseEntity> lockById(@Param("id") UUID id);

    Optional<ModerationCaseEntity> findByCaseTypeAndCaseStatusAndTargetCatalogItemId(
            ModerationCaseType type, ModerationCaseStatus status, UUID targetId);
    Optional<ModerationCaseEntity> findByCaseTypeAndCaseStatusAndTargetNeedRequestId(
            ModerationCaseType type, ModerationCaseStatus status, UUID targetId);
    Page<ModerationCaseEntity> findAllByCaseStatus(ModerationCaseStatus status, Pageable pageable);
    Page<ModerationCaseEntity> findAllByCaseStatusAndCaseTypeAndTargetType(
            ModerationCaseStatus status, ModerationCaseType caseType, ModerationTargetType targetType, Pageable pageable);
}
