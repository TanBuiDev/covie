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

public interface ModerationCaseRepository extends JpaRepository<ModerationCaseEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select c from ModerationCaseEntity c where c.id = :id") Optional<ModerationCaseEntity> findByIdForUpdate(@Param("id") UUID id);
    Optional<ModerationCaseEntity> findByTargetCatalogItemIdAndCaseTypeAndCaseStatus(UUID catalogItemId, vn.covie.moderation.model.ModerationCaseType caseType, ModerationCaseStatus caseStatus);
    Optional<ModerationCaseEntity> findByTargetNeedRequestIdAndCaseTypeAndCaseStatus(UUID needRequestId, vn.covie.moderation.model.ModerationCaseType caseType, ModerationCaseStatus caseStatus);
    Page<ModerationCaseEntity> findAllByCaseStatus(ModerationCaseStatus caseStatus, Pageable pageable);
}
