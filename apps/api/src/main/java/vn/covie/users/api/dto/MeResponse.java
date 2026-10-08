package vn.covie.users.api.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(UUID id, String displayName, String email, String accountStatus, long version, List<String> globalRoles,
        @io.swagger.v3.oas.annotations.media.Schema(nullable=true) String phone,
        @io.swagger.v3.oas.annotations.media.Schema(nullable=true) UUID avatarMediaId,
        @io.swagger.v3.oas.annotations.media.Schema(nullable=true) String avatarUrl) {
    public MeResponse(UUID id,String displayName,String email,String accountStatus,long version,List<String> roles){this(id,displayName,email,accountStatus,version,roles,null,null,null);}
}
