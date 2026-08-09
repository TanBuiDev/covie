package vn.covie.users.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.users.model.SystemRoleAssignmentEntity;

public interface SystemRoleAssignmentRepository extends JpaRepository<SystemRoleAssignmentEntity, UUID> { List<SystemRoleAssignmentEntity> findAllByUserId(UUID userId); }
