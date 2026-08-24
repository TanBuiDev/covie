package vn.covie.admin.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminMerchantSummaryResponse(UUID id,String name,String slug,String merchantStatus,String moderationStatus,long ownerCount,long memberCount,long locationCount,Instant createdAt,Instant updatedAt) {}
