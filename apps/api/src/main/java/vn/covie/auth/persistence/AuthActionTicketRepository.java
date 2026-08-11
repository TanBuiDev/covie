package vn.covie.auth.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;
import vn.covie.auth.model.AuthActionTicketEntity;

public interface AuthActionTicketRepository extends JpaRepository<AuthActionTicketEntity, UUID> {
    Optional<AuthActionTicketEntity> findByTokenHash(String tokenHash);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AuthActionTicketEntity t where t.tokenHash = :hash")
    Optional<AuthActionTicketEntity> findByTokenHashForUpdate(@Param("hash") String hash);
}
