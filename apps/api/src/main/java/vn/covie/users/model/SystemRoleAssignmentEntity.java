package vn.covie.users.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import vn.covie.shared.security.SystemRole;

@Entity
@Table(name = "system_role_assignments")
public class SystemRoleAssignmentEntity {
    @Id private UUID id;
    @Column(name = "user_id", nullable = false) private UUID userId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private SystemRole role;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected SystemRoleAssignmentEntity() {}
    public static SystemRoleAssignmentEntity assign(UUID id, UUID userId, SystemRole role, Instant now) { SystemRoleAssignmentEntity assignment = new SystemRoleAssignmentEntity(); assignment.id=id; assignment.userId=userId; assignment.role=role; assignment.createdAt=now; return assignment; }
    public SystemRole getRole() { return role; }
}
