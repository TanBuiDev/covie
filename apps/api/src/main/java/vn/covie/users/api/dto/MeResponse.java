package vn.covie.users.api.dto;

import java.util.List;
import java.util.UUID;

public record MeResponse(UUID id, String displayName, String email, String accountStatus, long version, List<String> globalRoles) {}
