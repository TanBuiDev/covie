package vn.covie.users.persistence;

import java.util.UUID;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.users.model.UserEntity;

public interface UserRepository extends JpaRepository<UserEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select u from UserEntity u where u.id = :id")
    Optional<UserEntity> findByIdForUpdate(@Param("id") UUID id);
    @Query("select u from UserEntity u where (:status is null or u.accountStatus = :status) and (:q is null or lower(u.displayName) like lower(concat('%', :q, '%')))")
    Page<UserEntity> searchAdmin(@Param("status") vn.covie.users.model.AccountStatus status, @Param("q") String q, Pageable pageable);
}
