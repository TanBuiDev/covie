package vn.covie.admin.api.dto;
import java.util.UUID;
public record ReputationRebuildRequest(UUID orderId,boolean rebuildAll){public ReputationRebuildRequest{if((orderId==null)==(!rebuildAll))throw new IllegalArgumentException("Specify one order or rebuildAll");}}
