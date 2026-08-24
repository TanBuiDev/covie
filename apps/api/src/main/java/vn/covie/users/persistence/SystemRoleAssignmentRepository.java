package vn.covie.users.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.users.model.SystemRoleAssignmentEntity;
import vn.covie.shared.security.SystemRole;

public interface SystemRoleAssignmentRepository extends JpaRepository<SystemRoleAssignmentEntity, UUID> {
    List<SystemRoleAssignmentEntity> findAllByUserId(UUID userId);
    @org.springframework.data.jpa.repository.Query("select count(a) from SystemRoleAssignmentEntity a join UserEntity u on u.id = a.userId where a.role = :role and u.accountStatus = vn.covie.users.model.AccountStatus.ACTIVE")
    long countActiveUsersWithRole(@org.springframework.data.repository.query.Param("role") SystemRole role);
}
