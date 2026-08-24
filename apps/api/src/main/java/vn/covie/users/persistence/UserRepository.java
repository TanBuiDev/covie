package vn.covie.users.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.users.model.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserEntity u where u.id=:id")
    Optional<UserEntity> lockById(@Param("id") UUID id);
}
