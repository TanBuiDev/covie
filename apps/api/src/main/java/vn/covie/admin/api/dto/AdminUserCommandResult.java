package vn.covie.admin.api.dto;

import java.util.UUID;
import vn.covie.users.model.AccountStatus;

public record AdminUserCommandResult(UUID userId, AccountStatus status) {}
