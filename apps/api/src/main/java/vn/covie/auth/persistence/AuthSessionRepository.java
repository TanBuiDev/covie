package vn.covie.auth.persistence;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.model.AuthSessionRevocationReason;

public interface AuthSessionRepository extends JpaRepository<AuthSessionEntity, UUID> {
    Optional<AuthSessionEntity> findByIdAndRevokedAtIsNull(UUID id);
    List<AuthSessionEntity> findAllByUserIdOrderByCreatedAtDesc(UUID userId);
    @Query("select s from AuthSessionEntity s where s.id = :id and s.revokedAt is null and exists (select 1 from UserEntity u where u.id = s.userId and u.accountStatus = vn.covie.users.model.AccountStatus.ACTIVE)")
    Optional<AuthSessionEntity> findUsableById(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from AuthSessionEntity s where s.id = :id")
    Optional<AuthSessionEntity> findByIdForUpdate(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from AuthSessionEntity s where s.userId = :userId and s.revokedAt is null order by s.id")
    List<AuthSessionEntity> findAllActiveByUserIdForUpdate(@Param("userId") UUID userId);
    @Modifying @Query("update AuthSessionEntity s set s.lastUsedAt = :now where s.id = :sessionId and s.revokedAt is null and s.lastUsedAt <= :cutoff")
    int touchLastUsedIfBefore(UUID sessionId, Instant now, Instant cutoff);
    @Modifying @Query("update AuthSessionEntity s set s.revokedAt = :now, s.revocationReason = :reason where s.userId = :userId and s.revokedAt is null")
    int revokeAllActive(UUID userId, Instant now, AuthSessionRevocationReason reason);
}
