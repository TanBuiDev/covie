package vn.covie.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.checkout.api.dto.CheckoutPreviewRequest;
import vn.covie.checkout.application.CheckoutCommitService;
import vn.covie.checkout.application.CheckoutPreviewService;
import vn.covie.checkout.model.CheckoutSourceType;
import vn.covie.checkout.model.FulfillmentMethod;
import vn.covie.orders.api.dto.OrderDetailResponse;
import vn.covie.orders.application.OrderReadService;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;

class OrderSnapshotImmutabilityIntegrationTest extends PostgresIntegrationTest {
    @Autowired CheckoutPreviewService previews;
    @Autowired CheckoutCommitService commits;
    @Autowired OrderReadService reads;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    @MockitoBean CurrentActorProvider actors;

    @ParameterizedTest
    @ValueSource(strings = {"FIXED", "NEGOTIATED"})
    void shippingChangeBeforeCommitRequiresFreshPreviewWithoutCreatingOrder(String mode) {
        Fixture fixture = seedFixture();
        actAs(fixture.buyerId(), SystemRole.USER);
        var request = new CheckoutPreviewRequest(CheckoutSourceType.DIRECT, null,
                List.of(new CheckoutPreviewRequest.DirectItem(fixture.productOfferId(), 1)),
                FulfillmentMethod.SELLER_DELIVERY, fixture.addressId(), PaymentMethod.COD);
        String token = previews.preview(request).checkoutToken();
        jdbc.update("delete from shipping_rules where shipping_policy_id=?", fixture.shippingPolicyId());
        jdbc.update("update shipping_policies set mode=?,base_fee=?,rule_match_mode=null,version=version+1 where id=?",
                mode, mode.equals("NEGOTIATED") ? null : 5000L, fixture.shippingPolicyId());
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> commits.commit(fixture.buyerId(), token))
                    .isInstanceOfSatisfying(ApiException.class, exception -> {
                        assertThat(exception.status()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(exception.code()).isEqualTo(ErrorCode.CHECKOUT_CHANGED);
                        assertThat(exception.details().get("changes").toString()).contains("SHIPPING_CHANGED");
                    });
        }
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=?", Long.class,
                fixture.buyerId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from checkout_sessions where user_id=? and consumed_at is not null",
                Long.class, fixture.buyerId())).isZero();
        UUID orderId = commits.commit(fixture.buyerId(), previews.preview(request).checkoutToken()).id();
        var pricing = reads.detail(orderId).pricing();
        assertThat(pricing.shippingFeeType().name()).isEqualTo(mode);
        if (mode.equals("NEGOTIATED")) {
            assertThat(pricing.shippingFee()).isNull();
            assertThat(pricing.totalAmount()).isNull();
            assertThat(pricing.knownTotal()).isEqualTo(10_000L);
        } else {
            assertThat(pricing.shippingFee()).isEqualTo(5_000L);
            assertThat(pricing.totalAmount()).isEqualTo(15_000L);
        }
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=?", Long.class,
                fixture.buyerId())).isEqualTo(1L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"FIXED", "CONDITIONAL", "NEGOTIATED"})
    void deliveryPricingRetainsPaidOrUnknownFeeAfterPolicyChanges(String mode) {
        Fixture fixture = seedFixture();
        jdbc.update("delete from shipping_rules where shipping_policy_id=?", fixture.shippingPolicyId());
        jdbc.update("update shipping_policies set mode=?,base_fee=?,rule_match_mode=? where id=?",
                mode, mode.equals("NEGOTIATED") ? null : 5000L,
                mode.equals("CONDITIONAL") ? "ANY" : null, fixture.shippingPolicyId());
        if (mode.equals("CONDITIONAL")) {
            jdbc.update("insert into shipping_rules(id,shipping_policy_id,condition_type,operator,threshold_value,"
                    + "action_type,priority,enabled) values (?,?,'ORDER_AMOUNT_MIN','GTE',20000,'FREE_SHIPPING',0,true)",
                    UUID.randomUUID(), fixture.shippingPolicyId());
        }
        actAs(fixture.buyerId(), SystemRole.USER);
        UUID orderId = checkout(fixture.productOfferId(), FulfillmentMethod.SELLER_DELIVERY, fixture.addressId());
        var original = reads.detail(orderId);
        assertThat(original.pricing().subtotal()).isEqualTo(10_000L);
        assertThat(original.pricing().shippingFeeType().name()).isEqualTo(mode);
        if (mode.equals("NEGOTIATED")) {
            assertThat(original.pricing().shippingFee()).isNull();
            assertThat(original.pricing().totalAmount()).isNull();
            assertThat(original.pricing().knownTotal()).isEqualTo(10_000L);
            assertThat(json(original.fulfillment().shippingPolicy()).path("effectiveShippingFee").isNull()).isTrue();
        } else {
            assertThat(original.pricing().shippingFee()).isEqualTo(5_000L);
            assertThat(original.pricing().totalAmount()).isEqualTo(15_000L);
            assertThat(original.pricing().knownTotal()).isEqualTo(15_000L);
            assertThat(json(original.fulfillment().shippingPolicy()).path("effectiveShippingFee").asLong())
                    .isEqualTo(5_000L);
        }
        assertThat(json(original.fulfillment().shippingPolicy()).path("matchedRuleIds")).isEmpty();
        jdbc.update("update shipping_policies set mode='FIXED',base_fee=99000,rule_match_mode=null,version=version+1 where id=?",
                fixture.shippingPolicyId());
        var after = reads.detail(orderId);
        assertThat(after.pricing()).isEqualTo(original.pricing());
        assertThat(after.fulfillment().shippingPolicy()).isEqualTo(original.fulfillment().shippingPolicy());
    }

    @Test
    void committedOrdersRetainFulfillmentSnapshotsForEveryAuthorizedRoleAfterSourcesChange() {
        Fixture fixture = seedFixture();
        actAs(fixture.buyerId(), SystemRole.USER);

        UUID productOrderId = checkout(fixture.productOfferId(), FulfillmentMethod.SELLER_DELIVERY,
                fixture.addressId());
        UUID serviceOrderId = checkout(fixture.serviceOfferId(), FulfillmentMethod.SERVICE,
                fixture.addressId());
        int productDistance = distance("product_fulfillments", productOrderId);
        int serviceDistance = distance("service_fulfillments", serviceOrderId);
        assertThat(productDistance).isPositive();
        assertThat(serviceDistance).isPositive();

        var storedSummaries = reads.myOrders(fixture.buyerId()).items();
        assertThat(storedSummaries).hasSize(2);
        assertThat(storedSummaries.stream().flatMap(order -> order.items().stream()).map(item -> item.name()))
                .containsExactlyInAnyOrder("Original product", "Original service");

        mutateSources(fixture);

        assertThat(reads.myOrders(fixture.buyerId()).items()).isEqualTo(storedSummaries);
        var firstSummaryPage = reads.myOrders(fixture.buyerId(), 1, null);
        assertThat(firstSummaryPage.items()).hasSize(1);
        assertThat(firstSummaryPage.nextCursor()).isNotBlank();
        var secondSummaryPage = reads.myOrders(fixture.buyerId(), 1, firstSummaryPage.nextCursor());
        assertThat(secondSummaryPage.items()).hasSize(1);
        assertThat(secondSummaryPage.nextCursor()).isNull();
        var pagedSummaries = new java.util.ArrayList<>(firstSummaryPage.items());
        pagedSummaries.addAll(secondSummaryPage.items());
        assertThat(pagedSummaries).containsExactlyInAnyOrderElementsOf(storedSummaries);
        assertThat(reads.myOrders(fixture.unrelatedId(), 1, null).items()).isEmpty();

        for (Actor actor : List.of(
                new Actor(fixture.buyerId(), Set.of(SystemRole.USER)),
                new Actor(fixture.merchantUserId(), Set.of(SystemRole.USER)),
                new Actor(fixture.adminId(), Set.of(SystemRole.ADMIN)))) {
            actAs(actor.userId(), actor.roles().toArray(SystemRole[]::new));
            assertProductSnapshots(reads.detail(productOrderId), fixture, productDistance);
            assertServiceSnapshots(reads.detail(serviceOrderId), fixture, serviceDistance);
        }

        actAs(fixture.unrelatedId(), SystemRole.USER);
        for (UUID orderId : List.of(productOrderId, serviceOrderId)) {
            assertThatThrownBy(() -> reads.detail(orderId))
                    .isInstanceOfSatisfying(ApiException.class, exception -> {
                        assertThat(exception.status()).isEqualTo(HttpStatus.NOT_FOUND);
                        assertThat(exception.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
                        assertThat(exception.details()).isEmpty();
                    });
        }
    }

    private UUID checkout(UUID offerId, FulfillmentMethod fulfillmentMethod, UUID addressId) {
        CheckoutPreviewRequest request = new CheckoutPreviewRequest(CheckoutSourceType.DIRECT, null,
                List.of(new CheckoutPreviewRequest.DirectItem(offerId, 1)), fulfillmentMethod, addressId,
                PaymentMethod.COD);
        String token = previews.preview(request).checkoutToken();
        return commits.commit(currentActor().userId(), token).id();
    }

    private void assertProductSnapshots(OrderDetailResponse order, Fixture fixture, int distance) {
        assertThat(order.pricing().subtotal()).isEqualTo(10_000L);
        assertThat(order.pricing().shippingFee()).isZero();
        assertThat(order.pricing().knownTotal()).isEqualTo(10_000L);
        assertThat(order.pricing().totalAmount()).isEqualTo(10_000L);
        JsonNode item = json(order.items().getFirst().snapshot());
        assertThat(item.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(item.path("name").asText()).isEqualTo("Original product");
        assertThat(item.path("variantName").asText()).isEqualTo("Original product variant");
        assertThat(item.path("sku").asText()).isEqualTo("PRODUCT-SKU");
        assertThat(item.path("unitPrice").asLong()).isEqualTo(10_000L);
        assertThat(item.path("preparationTimeMinutes").asInt()).isEqualTo(15);

        var fulfillment = order.fulfillment();
        assertThat(fulfillment.distanceMeters()).isEqualTo(distance);
        assertAddressSnapshot(json(fulfillment.address()));
        assertBuyerContactSnapshot(json(fulfillment.buyerContact()));
        assertMerchantContactSnapshot(json(fulfillment.merchantContact()), fixture.publicContactId());
        assertLocationSnapshot(json(fulfillment.pickupLocation()));
        assertThat(fulfillment.providerLocation()).isNull();

        JsonNode policy = json(fulfillment.shippingPolicy());
        assertThat(policy.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(policy.path("policyId").asText()).isEqualTo(fixture.shippingPolicyId().toString());
        assertThat(policy.path("baseFee").asLong()).isEqualTo(5_000L);
        assertThat(policy.path("distanceMetersUsed").asInt()).isEqualTo(distance);
        assertThat(policy.path("matchedRuleIds").get(0).asText()).isEqualTo(fixture.shippingRuleId().toString());
        assertThat(policy.path("effectiveShippingFee").asLong()).isZero();
        assertThat(policy.path("rules").get(0).path("thresholdValue").asLong()).isEqualTo(2_000L);
        assertThat(policy.path("rules").get(0).path("enabled").asBoolean()).isTrue();
    }

    private void assertServiceSnapshots(OrderDetailResponse order, Fixture fixture, int distance) {
        JsonNode item = json(order.items().getFirst().snapshot());
        assertThat(item.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(item.path("name").asText()).isEqualTo("Original service");
        assertThat(item.path("variantName").asText()).isEqualTo("Original service variant");
        assertThat(item.path("sku").asText()).isEqualTo("SERVICE-SKU");
        assertThat(item.path("unitPrice").asLong()).isEqualTo(20_000L);
        assertThat(item.path("preparationTimeMinutes").asInt()).isEqualTo(30);

        var fulfillment = order.fulfillment();
        assertThat(fulfillment.distanceMeters()).isEqualTo(distance);
        assertAddressSnapshot(json(fulfillment.address()));
        assertBuyerContactSnapshot(json(fulfillment.buyerContact()));
        assertMerchantContactSnapshot(json(fulfillment.merchantContact()), fixture.publicContactId());
        assertLocationSnapshot(json(fulfillment.providerLocation()));
        assertThat(fulfillment.pickupLocation()).isNull();
        assertThat(fulfillment.shippingPolicy()).isNull();
    }

    private static void assertAddressSnapshot(JsonNode address) {
        assertThat(address.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(address.path("label").asText()).isEqualTo("Original address");
        assertThat(address.path("recipientName").asText()).isEqualTo("Original buyer");
        assertThat(address.path("phone").asText()).isEqualTo("0900000001");
        assertThat(address.path("addressText").asText()).isEqualTo("10 Original Street");
        assertThat(address.path("latitude").asDouble()).isEqualTo(10.805);
        assertThat(address.path("longitude").asDouble()).isEqualTo(106.705);
    }

    private static void assertBuyerContactSnapshot(JsonNode contact) {
        assertThat(contact.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(contact.path("recipientName").asText()).isEqualTo("Original buyer");
        assertThat(contact.path("phone").asText()).isEqualTo("0900000001");
    }

    private static void assertMerchantContactSnapshot(JsonNode contact, UUID publicContactId) {
        assertThat(contact.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(contact.path("contacts")).hasSize(1);
        JsonNode value = contact.path("contacts").get(0);
        assertThat(value.path("id").asText()).isEqualTo(publicContactId.toString());
        assertThat(value.path("type").asText()).isEqualTo("PHONE");
        assertThat(value.path("value").asText()).isEqualTo("0900000002");
        assertThat(value.path("primary").asBoolean()).isTrue();
    }

    private static void assertLocationSnapshot(JsonNode location) {
        assertThat(location.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(location.path("name").asText()).isEqualTo("Original location");
        assertThat(location.path("addressText").asText()).isEqualTo("1 Original Market");
        assertThat(location.path("latitude").asDouble()).isEqualTo(10.8);
        assertThat(location.path("longitude").asDouble()).isEqualTo(106.7);
        assertThat(location.path("sellerDeliveryEnabled").asBoolean()).isTrue();
        assertThat(location.path("serviceEnabled").asBoolean()).isTrue();
    }

    private JsonNode json(Object value) {
        return mapper.valueToTree(value);
    }

    private Fixture seedFixture() {
        Instant now = Instant.now();
        Timestamp timestamp = Timestamp.from(now);
        UUID buyer = UUID.randomUUID();
        UUID merchantUser = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        UUID unrelated = UUID.randomUUID();
        for (UUID userId : List.of(buyer, merchantUser, admin, unrelated)) {
            jdbc.update("insert into users(id,display_name,account_status,created_at,updated_at,version) "
                    + "values (?,?,'ACTIVE',?,?,0)", userId, "user-" + userId, timestamp, timestamp);
        }

        UUID merchant = UUID.randomUUID();
        UUID membership = UUID.randomUUID();
        UUID location = UUID.randomUUID();
        UUID address = UUID.randomUUID();
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version) "
                + "values (?,?,?,'ACTIVE','APPROVED',?,?,0)", merchant, "Original merchant",
                "merchant-" + merchant, timestamp, timestamp);
        jdbc.update("insert into merchant_memberships(id,merchant_profile_id,user_id,role,membership_status,joined_at,created_at,version) "
                + "values (?,?,?,'OWNER','ACTIVE',?,?,0)", membership, merchant, merchantUser, timestamp,
                timestamp);
        jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,"
                + "location_status,pickup_enabled,seller_delivery_enabled,service_enabled,minimum_delivery_order,currency_code,"
                + "created_at,updated_at,version) values (?,?,?, ?,ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),"
                + "'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,true,true,0,'VND',?,?,0)", location, merchant,
                "Original location", "1 Original Market", timestamp, timestamp);
        jdbc.update("insert into user_addresses(id,user_id,label,recipient_name,phone,address_text,geo_point,pin_source,"
                + "is_default,status,created_at,updated_at,version) values (?,?,?,?,?,?,"
                + "ST_GeogFromText('SRID=4326;POINT(106.705 10.805)'),'GPS',true,'ACTIVE',?,?,0)", address,
                buyer, "Original address", "Original buyer", "0900000001", "10 Original Street", timestamp,
                timestamp);

        ItemIds product = insertItem(merchant, location, CatalogItemType.PRODUCT, "Original product",
                "Original product variant", "PRODUCT-SKU", 10_000L, 15, timestamp);
        ItemIds service = insertItem(merchant, location, CatalogItemType.SERVICE, "Original service",
                "Original service variant", "SERVICE-SKU", 20_000L, 30, timestamp);

        UUID productCoverage = UUID.randomUUID();
        UUID serviceCoverage = UUID.randomUUID();
        jdbc.update("insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,"
                + "area_enabled,coverage_status,created_at,updated_at,version) values (?,?,'PRODUCT_DELIVERY',true,5000,"
                + "false,'ACTIVE',?,?,0)", productCoverage, location, timestamp, timestamp);
        jdbc.update("insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,"
                + "area_enabled,coverage_status,created_at,updated_at,version) values (?,?,'SERVICE_AREA',true,5000,"
                + "false,'ACTIVE',?,?,0)", serviceCoverage, location, timestamp, timestamp);

        UUID policy = UUID.randomUUID();
        UUID rule = UUID.randomUUID();
        jdbc.update("insert into shipping_policies(id,merchant_location_id,mode,base_fee,currency_code,rule_match_mode,"
                + "policy_status,created_at,updated_at,version) values (?,?,'CONDITIONAL',5000,'VND','ANY','ACTIVE',?,?,0)",
                policy, location, timestamp, timestamp);
        jdbc.update("insert into shipping_rules(id,shipping_policy_id,condition_type,operator,threshold_value,action_type,"
                + "priority,enabled) values (?,?,'DISTANCE_MAX','LTE',2000,'FREE_SHIPPING',0,true)", rule, policy);

        UUID publicContact = UUID.randomUUID();
        UUID privateContact = UUID.randomUUID();
        jdbc.update("insert into merchant_contact_methods(id,merchant_profile_id,merchant_location_id,contact_type,value,"
                + "is_primary,is_public,status,created_at,version) values (?,?,null,'PHONE',?,true,true,'ACTIVE',?,0)",
                publicContact, merchant, "0900000002", timestamp);
        jdbc.update("insert into merchant_contact_methods(id,merchant_profile_id,merchant_location_id,contact_type,value,"
                + "is_primary,is_public,status,created_at,version) values (?,?,?,'OTHER',?,false,false,'ACTIVE',?,0)",
                privateContact, merchant, location, "private-value", timestamp);

        return new Fixture(buyer, merchantUser, admin, unrelated, merchant, location, address,
                product.itemId(), product.variantId(), product.offerId(), service.itemId(), service.variantId(),
                service.offerId(), publicContact, policy, rule);
    }

    private ItemIds insertItem(UUID merchantId, UUID locationId, CatalogItemType type, String itemName,
            String variantName, String sku, long price, int preparationMinutes, Timestamp timestamp) {
        UUID category = UUID.randomUUID();
        UUID item = UUID.randomUUID();
        UUID variant = UUID.randomUUID();
        UUID offer = UUID.randomUUID();
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,?,?,?,'ACTIVE',0,0)",
                category, type.name() + " category", "category-" + category, type.name());
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,"
                + "listing_status,moderation_status,created_at,updated_at,version) values (?,?,?,'STANDARD',?,?,?,"
                + "'ACTIVE','APPROVED',?,?,0)", item, merchantId, type.name(), category, itemName, "item-" + item,
                timestamp, timestamp);
        jdbc.update("insert into variants(id,catalog_item_id,sku,variant_name,is_default,variant_status,sort_order,"
                + "created_at,updated_at,version) values (?,?,?,?,true,'ACTIVE',0,?,?,0)", variant, item, sku,
                variantName, timestamp, timestamp);
        jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,"
                + "inventory_mode,preparation_time_minutes,created_at,updated_at,version) values (?,?,?,?,'VND',true,"
                + "'UNTRACKED',?,?,?,0)", offer, locationId, variant, price, preparationMinutes, timestamp, timestamp);
        return new ItemIds(item, variant, offer);
    }

    private void mutateSources(Fixture fixture) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("update catalog_items set name='Changed item',updated_at=?,version=version+1 where id in (?,?)",
                now, fixture.productItemId(), fixture.serviceItemId());
        jdbc.update("update variants set variant_name='Changed variant',sku='CHANGED-SKU',updated_at=?,version=version+1 "
                + "where id in (?,?)", now, fixture.productVariantId(), fixture.serviceVariantId());
        jdbc.update("update location_variants set price=99999,preparation_time_minutes=99,updated_at=?,version=version+1 "
                + "where id in (?,?)", now, fixture.productOfferId(), fixture.serviceOfferId());
        jdbc.update("update user_addresses set label='Changed address',recipient_name='Changed buyer',phone='0999999999',"
                + "address_text='Changed Street',geo_point=ST_GeogFromText('SRID=4326;POINT(107 11)'),updated_at=?,"
                + "version=version+1 where id=?", now, fixture.addressId());
        jdbc.update("update merchant_locations set name='Changed location',address_text='Changed Market',"
                + "geo_point=ST_GeogFromText('SRID=4326;POINT(107 11)'),updated_at=?,version=version+1 where id=?",
                now, fixture.locationId());
        jdbc.update("update merchant_contact_methods set value='0999999998',version=version+1 where id=?",
                fixture.publicContactId());
        jdbc.update("update shipping_policies set base_fee=99000,updated_at=?,version=version+1 where id=?", now,
                fixture.shippingPolicyId());
        jdbc.update("update shipping_rules set threshold_value=1,enabled=false where id=?", fixture.shippingRuleId());
    }

    private int distance(String table, UUID orderId) {
        return jdbc.queryForObject("select distance_meters from " + table + " where order_id=?", Integer.class,
                orderId);
    }

    private void actAs(UUID userId, SystemRole... roles) {
        when(actors.currentActor()).thenReturn(new CurrentActor(userId, Set.of(roles), UUID.randomUUID()));
    }

    private CurrentActor currentActor() { return actors.currentActor(); }

    private record Actor(UUID userId, Set<SystemRole> roles) {}
    private record ItemIds(UUID itemId, UUID variantId, UUID offerId) {}
    private record Fixture(UUID buyerId, UUID merchantUserId, UUID adminId, UUID unrelatedId, UUID merchantId,
            UUID locationId, UUID addressId, UUID productItemId, UUID productVariantId, UUID productOfferId,
            UUID serviceItemId, UUID serviceVariantId, UUID serviceOfferId, UUID publicContactId,
            UUID shippingPolicyId, UUID shippingRuleId) {}
}
