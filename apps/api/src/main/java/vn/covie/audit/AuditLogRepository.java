package vn.covie.audit;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, UUID> {
    @Query("select a from AuditLogEntity a where (:actorUserId is null or a.actorUserId = :actorUserId) and (:action is null or a.action = :action) and (:entityType is null or a.entityType = :entityType) and (:entityId is null or a.entityId = :entityId) and (:requestId is null or a.requestId = :requestId) and (:createdFrom is null or a.createdAt >= :createdFrom) and (:createdTo is null or a.createdAt <= :createdTo)")
    Page<AuditLogEntity> findAdminPage(@Param("actorUserId") UUID actorUserId, @Param("action") String action,
            @Param("entityType") String entityType, @Param("entityId") UUID entityId, @Param("requestId") String requestId,
            @Param("createdFrom") Instant createdFrom, @Param("createdTo") Instant createdTo, Pageable pageable);
}
