package vn.covie.admin.api.dto;

import java.util.UUID;
import vn.covie.merchants.model.MerchantStatus;

public record AdminMerchantCommandResult(UUID merchantId, MerchantStatus status) {}
