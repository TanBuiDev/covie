package vn.covie.admin.api.dto;
import java.util.UUID;
public record RatingRebuildRequest(UUID catalogItemId,UUID merchantId){public RatingRebuildRequest{if((catalogItemId==null)==(merchantId==null))throw new IllegalArgumentException("Exactly one rating rebuild target is required");}}
