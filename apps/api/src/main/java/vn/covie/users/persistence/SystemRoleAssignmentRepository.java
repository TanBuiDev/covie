package vn.covie.users.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.shared.security.SystemRole;
import vn.covie.users.model.SystemRoleAssignmentEntity;

public interface SystemRoleAssignmentRepository extends JpaRepository<SystemRoleAssignmentEntity, UUID> {
    List<SystemRoleAssignmentEntity> findAllByUserId(UUID userId);
    boolean existsByUserIdAndRole(UUID userId, SystemRole role);

    @Query("select count(a) from SystemRoleAssignmentEntity a join UserEntity u on u.id=a.userId where a.role=vn.covie.shared.security.SystemRole.ADMIN and u.accountStatus=vn.covie.users.model.AccountStatus.ACTIVE")
    long countActiveAdmins();
}
