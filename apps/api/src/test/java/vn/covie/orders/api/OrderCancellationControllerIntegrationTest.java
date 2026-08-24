package vn.covie.orders.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.auth.model.AuthSessionEntity;
import vn.covie.auth.persistence.AuthSessionRepository;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class OrderCancellationControllerIntegrationTest extends PostgresIntegrationTest {
    @Autowired WebApplicationContext web;
    @Autowired UserRepository users;
    @Autowired AuthSessionRepository sessions;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantLocationRepository locations;
    @Autowired OrderRepository orders;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    private MockMvc mockMvc;

    @BeforeEach void setUp() { mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build(); }

    @Test
    void merchantCancelsConfirmedOrderAndIdempotentlyReplaysTheSingleEventAndOutboxRecord() throws Exception {
        Fixture fixture = fixture();
        seedActiveReservation(fixture);
        String path = "/api/v1/orders/" + fixture.orderId() + "/cancel";
        String body = "{\"reasonCode\":\"MERCHANT_UNAVAILABLE\",\"note\":\"Closed\"}";

        mockMvc.perform(post(path).with(fixture.seller().jwt()).header("Idempotency-Key", "seller-cancel-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_SELLER_AFTER_CONFIRMATION"));
        mockMvc.perform(post(path).with(fixture.seller().jwt()).header("Idempotency-Key", "seller-cancel-key")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED_BY_SELLER_AFTER_CONFIRMATION"));

        assertThat(orders.findById(fixture.orderId()).orElseThrow().getStatus())
                .isEqualTo(OrderStatus.CANCELLED_BY_SELLER_AFTER_CONFIRMATION);
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id = ?", Integer.class, fixture.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id = ?", Integer.class, fixture.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_reservations where order_id = ? and reservation_status = 'RELEASED'", Integer.class, fixture.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id = ? and movement_type = 'RELEASE_RESERVATION'", Integer.class, fixture.orderId())).isEqualTo(1);
    }

    @Test
    void merchantCancellationRequiresReason() throws Exception {
        Fixture fixture = fixture();
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", fixture.orderId()).with(fixture.seller().jwt())
                        .header("Idempotency-Key", "missing-reason").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
    }

    @Test
    void buyerMustRequestRatherThanDirectlyCancelAConfirmedOrder() throws Exception {
        Fixture fixture = fixture();
        mockMvc.perform(post("/api/v1/orders/{orderId}/cancel", fixture.orderId()).with(fixture.buyer().jwt())
                        .header("Idempotency-Key", "buyer-direct-cancel").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"CHANGED_MIND\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ORDER_ACTION_NOT_ALLOWED"));
    }

    private Fixture fixture() {
        Instant now = Instant.now();
        Actor seller = actor("seller", now); Actor buyer = actor("buyer", now);
        UUID merchantId = UUID.randomUUID(), locationId = UUID.randomUUID(), orderId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Merchant", "merchant-" + merchantId, now));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, seller.userId(), MerchantRole.OWNER, now));
        locations.saveAndFlush(MerchantLocationEntity.create(locationId, merchantId, "Main", "1 CoVie", 10.8, 106.7, now));
        OrderEntity order = OrderEntity.pending(orderId, "CV" + orderId.toString().substring(0, 8), OrderType.PRODUCT,
                buyer.userId(), merchantId, locationId, PaymentMethod.COD, 10_000, OrderShippingFeeType.FREE,
                0L, 10_000, 10_000L, "VND", now.plusSeconds(600), now);
        order.confirm(now);
        orders.saveAndFlush(order);
        return new Fixture(seller, buyer, merchantId, locationId, orderId);
    }

    private void seedActiveReservation(Fixture fixture) {
        UUID categoryId = UUID.randomUUID(), itemId = UUID.randomUUID(), variantId = UUID.randomUUID();
        UUID offerId = UUID.randomUUID(), orderItemId = UUID.randomUUID(), reservationId = UUID.randomUUID();
        Timestamp now = Timestamp.from(Instant.now());
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,?,?,'PRODUCT','ACTIVE',0,0)", categoryId, "Food", "food-" + categoryId);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at,version) values (?,?,'PRODUCT','STANDARD',?,?,?,'ACTIVE','APPROVED',?,?,0)", itemId, fixture.merchantId(), categoryId, "Rice", "rice-" + itemId, now, now);
        jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version) values (?,?,?,true,'ACTIVE',?,?,0)", variantId, itemId, "Default", now, now);
        jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,created_at,updated_at,version) values (?,?,?,10000,'VND',true,'TRACKED',?,?,0)", offerId, fixture.locationId(), variantId, now, now);
        jdbc.update("insert into inventory_balances(location_variant_id,stock_on_hand,reserved_quantity,updated_at,version) values (?,?,?, ?,0)", offerId, 2, 1, now);
        jdbc.update("insert into order_items(id,order_id,catalog_item_id,variant_id,location_variant_id,item_type,quantity,unit_price,line_total,currency_code,item_snapshot,created_at) values (?,?,?,?,?,'PRODUCT',1,10000,10000,'VND','{}'::jsonb,?)", orderItemId, fixture.orderId(), itemId, variantId, offerId, now);
        jdbc.update("insert into inventory_reservations(id,location_variant_id,order_id,order_item_id,quantity,reservation_status,created_at) values (?,?,?,?,1,'ACTIVE',?)", reservationId, offerId, fixture.orderId(), orderItemId, now);
    }

    private Actor actor(String prefix, Instant now) {
        UUID userId = UUID.randomUUID(), sessionId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(userId, prefix + "-" + userId, now));
        sessions.saveAndFlush(AuthSessionEntity.create(sessionId, userId, UUID.randomUUID(), now, now.plusSeconds(86_400)));
        return new Actor(userId, sessionId);
    }

    private record Fixture(Actor seller, Actor buyer, UUID merchantId, UUID locationId, UUID orderId) {}
    private record Actor(UUID userId, UUID sessionId) {
        org.springframework.test.web.servlet.request.RequestPostProcessor jwt() {
            return SecurityMockMvcRequestPostProcessors.jwt().jwt(token -> token.subject(userId.toString())
                    .claim("sessionId", sessionId.toString()).claim("roles", List.of("USER")))
                    .authorities(new SimpleGrantedAuthority("ROLE_USER"));
        }
    }
}
