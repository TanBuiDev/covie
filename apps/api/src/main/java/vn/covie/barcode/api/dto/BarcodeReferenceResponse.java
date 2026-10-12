package vn.covie.barcode.api.dto;

public record BarcodeReferenceResponse(
    String gtin,
    String gtin14,
    String productName,
    String brand,
    String quantity,
    String categoryLabel,
    String marketCountry,
    String marketSignal,
    String source,
    String sourceRecordUrl,
    String licenseId,
    String qualityStatus,
    boolean needsReview,
    String attributionNotice
) {}
