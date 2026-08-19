package vn.covie.inventory.api.dto; import java.util.List; public record BulkSetStockRequest(String reasonCode,String note,List<BulkSetStockItem> items) {}
