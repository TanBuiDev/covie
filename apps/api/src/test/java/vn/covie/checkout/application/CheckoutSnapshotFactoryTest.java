package vn.covie.checkout.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.catalog.model.CatalogItemEntity;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.InventoryMode;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.LocationVariantEntity;
import vn.covie.catalog.model.VariantEntity;
import vn.covie.merchants.model.ContactType;
import vn.covie.merchants.model.MerchantContactMethodEntity;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.users.model.PinSource;
import vn.covie.users.model.UserAddressEntity;

class CheckoutSnapshotFactoryTest {
    private static final Instant NOW = Instant.parse("2026-09-12T00:00:00Z");
    private final CheckoutSnapshotFactory factory = new CheckoutSnapshotFactory(new ObjectMapper());

    @Test
    void createsVersionedItemAddressLocationAndContactSnapshots() {
        UUID merchantId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        CatalogItemEntity item = CatalogItemEntity.create(itemId, merchantId, CatalogItemType.PRODUCT,
                ListingKind.RESALE, UUID.randomUUID(), "Máy lọc", "may-loc", "Mô tả", NOW);
        VariantEntity variant = VariantEntity.create(variantId, itemId, "SKU-1", "Màu trắng", true, 0, NOW);
        LocationVariantEntity offer = LocationVariantEntity.create(UUID.randomUUID(), UUID.randomUUID(), variantId,
                250_000L, "VND", true, InventoryMode.TRACKED, 30, NOW);
        UserAddressEntity address = UserAddressEntity.create(UUID.randomUUID(), UUID.randomUUID(), "Nhà", "Bùi Tấn",
                "0900000000", "2 Đường Mới", 10.8, 106.7, PinSource.USER_ADJUSTED, "Gọi trước", true, NOW);
        MerchantLocationEntity location = MerchantLocationEntity.create(offer.getMerchantLocationId(), merchantId,
                "Chi nhánh 1", "1 Đường Mới", UUID.randomUUID(), UserAddressEntity.point(106.71, 10.81),
                vn.covie.merchants.model.PinSource.GPS, "Asia/Ho_Chi_Minh", true, true, false, 50_000L, "VND", NOW);
        MerchantContactMethodEntity contact = MerchantContactMethodEntity.create(UUID.randomUUID(), merchantId,
                location.getId(), ContactType.PHONE, "0280000000", null, true, true, NOW);

        var itemSnapshot = factory.item(item, variant, offer);
        var addressSnapshot = factory.address(address);
        var locationSnapshot = factory.location(location);
        var buyerContact = factory.buyerContact(address);
        var merchantContact = factory.merchantContact(List.of(contact));

        assertThat(itemSnapshot.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(itemSnapshot.path("name").asText()).isEqualTo("Máy lọc");
        assertThat(itemSnapshot.path("itemType").asText()).isEqualTo("PRODUCT");
        assertThat(itemSnapshot.path("listingKind").asText()).isEqualTo("RESALE");
        assertThat(itemSnapshot.path("variantName").asText()).isEqualTo("Màu trắng");
        assertThat(itemSnapshot.path("sku").asText()).isEqualTo("SKU-1");
        assertThat(itemSnapshot.path("unitPrice").asLong()).isEqualTo(250_000L);
        assertThat(itemSnapshot.path("preparationTimeMinutes").asInt()).isEqualTo(30);
        assertThat(addressSnapshot.path("recipientName").asText()).isEqualTo("Bùi Tấn");
        assertThat(addressSnapshot.path("latitude").asDouble()).isEqualTo(10.8);
        assertThat(locationSnapshot.path("timezone").asText()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(locationSnapshot.path("sellerDeliveryEnabled").asBoolean()).isTrue();
        assertThat(buyerContact.path("phone").asText()).isEqualTo("0900000000");
        assertThat(buyerContact.has("note")).isFalse();
        assertThat(merchantContact.path("contacts").get(0).path("type").asText()).isEqualTo("PHONE");
        assertThat(merchantContact.path("contacts").get(0).path("value").asText()).isEqualTo("0280000000");
    }

    @Test
    void createsAnEmptyVersionedBuyerContactForPickup() {
        var snapshot = factory.buyerContact(null);

        assertThat(snapshot.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(snapshot.path("recipientName").isNull()).isTrue();
        assertThat(snapshot.path("phone").isNull()).isTrue();
    }
}
