package vn.covie.admin.api.dto;
import java.time.Instant; import java.util.UUID; import vn.covie.merchants.model.*;
public record AdminMerchantSummaryResponse(UUID id,String name,String slug,MerchantStatus status,MerchantModerationStatus moderationStatus,Instant createdAt,Instant updatedAt) {}
