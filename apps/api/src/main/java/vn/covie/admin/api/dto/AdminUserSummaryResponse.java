package vn.covie.admin.api.dto;
import java.time.Instant; import java.util.Set; import java.util.UUID; import vn.covie.users.model.AccountStatus;
public record AdminUserSummaryResponse(UUID id,String displayName,AccountStatus status,Set<String> globalRoles,Instant createdAt,Instant updatedAt) {}
