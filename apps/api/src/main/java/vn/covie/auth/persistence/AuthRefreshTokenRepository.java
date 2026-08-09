package vn.covie.auth.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.auth.model.AuthRefreshTokenEntity;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshTokenEntity, UUID> {
    Optional<AuthRefreshTokenEntity> findByTokenHash(String tokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from AuthRefreshTokenEntity t where t.id = :id")
    Optional<AuthRefreshTokenEntity> findByIdForUpdate(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select t from AuthRefreshTokenEntity t where t.sessionId = :sessionId order by t.createdAt, t.id")
    List<AuthRefreshTokenEntity> findAllBySessionIdForUpdate(@Param("sessionId") UUID sessionId);
}
