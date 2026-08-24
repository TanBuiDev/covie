package vn.covie.admin.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserSummaryResponse(UUID id,String displayName,String email,String accountStatus,List<String> globalRoles,long activeSessionCount,Instant createdAt,Instant updatedAt){
    public AdminUserSummaryResponse { globalRoles=List.copyOf(globalRoles); }
}
