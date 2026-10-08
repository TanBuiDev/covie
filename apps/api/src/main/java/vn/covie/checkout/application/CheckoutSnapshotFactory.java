package vn.covie.checkout.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.merchants.model.MerchantContactMethodEntity;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.users.model.UserAddressEntity;

@Component
public class CheckoutSnapshotFactory {
    private static final int SCHEMA_VERSION = 1;
    private final ObjectMapper mapper;

    public CheckoutSnapshotFactory(ObjectMapper mapper) { this.mapper = mapper; }

    public JsonNode item(CatalogItemEntity item, VariantEntity variant, LocationVariantEntity offer) {
        return mapper.valueToTree(new ItemSnapshot(SCHEMA_VERSION, item.getId(), item.getName(), item.getItemType(),
                item.getListingKind(), item.getVersion(), variant.getId(), variant.getVariantName(), variant.getSku(),
                variant.getVersion(), offer.getId(), offer.getPrice(), offer.getCurrencyCode(),
                offer.getPreparationTimeMinutes(), offer.getVersion()));
    }

    public JsonNode address(UserAddressEntity address) {
        if (address == null) return null;
        return mapper.valueToTree(new AddressSnapshot(SCHEMA_VERSION, address.getId(), address.getLabel(),
                address.getRecipientName(), address.getPhone(), address.getAddressText(), address.getLocationAreaId(),
                address.getGeoPoint().getY(), address.getGeoPoint().getX(), address.getPinSource().name(),
                address.getVersion()));
    }

    public JsonNode location(MerchantLocationEntity location) {
        return mapper.valueToTree(new LocationSnapshot(SCHEMA_VERSION, location.getId(), location.getMerchantProfileId(),
                location.getName(), location.getAddressText(), location.getLocationAreaId(),
                location.getGeoPoint().getY(), location.getGeoPoint().getX(), location.getPinSource().name(),
                location.getTimezone(), location.isPickupEnabled(), location.isSellerDeliveryEnabled(),
                location.isServiceEnabled(), location.getMinimumDeliveryOrder(), location.getCurrencyCode(),
                location.getVersion()));
    }

    public JsonNode buyerContact(UserAddressEntity address) {
        return mapper.valueToTree(new BuyerContactSnapshot(SCHEMA_VERSION,
                address == null ? null : address.getRecipientName(), address == null ? null : address.getPhone()));
    }

    public JsonNode merchantContact(List<MerchantContactMethodEntity> contacts) {
        List<MerchantContactEntrySnapshot> values = contacts.stream().map(contact -> new MerchantContactEntrySnapshot(
                contact.getId(), contact.getMerchantLocationId(), contact.getContactType().name(), contact.getValue(),
                contact.getQrMediaId(), contact.isPrimary(), contact.getVersion())).toList();
        return mapper.valueToTree(new MerchantContactSnapshot(SCHEMA_VERSION, values));
    }

    record ItemSnapshot(int schemaVersion, UUID catalogItemId, String name, CatalogItemType itemType,
            ListingKind listingKind, long catalogItemVersion, UUID variantId, String variantName, String sku,
            long variantVersion, UUID locationVariantId, long unitPrice, String currency,
            Integer preparationTimeMinutes, long locationVariantVersion) {}

    record AddressSnapshot(int schemaVersion, UUID id, String label, String recipientName, String phone,
            String addressText, UUID locationAreaId, double latitude, double longitude, String pinSource,
            long sourceVersion) {}

    record LocationSnapshot(int schemaVersion, UUID id, UUID merchantProfileId, String name, String addressText,
            UUID locationAreaId, double latitude, double longitude, String pinSource, String timezone,
            boolean pickupEnabled, boolean sellerDeliveryEnabled, boolean serviceEnabled, Long minimumDeliveryOrder,
            String currency, long sourceVersion) {}

    record BuyerContactSnapshot(int schemaVersion, String recipientName, String phone) {}

    record MerchantContactSnapshot(int schemaVersion, List<MerchantContactEntrySnapshot> contacts) {}

    record MerchantContactEntrySnapshot(UUID id, UUID merchantLocationId, String type, String value, UUID qrMediaId,
            boolean primary, long sourceVersion) {}
}
