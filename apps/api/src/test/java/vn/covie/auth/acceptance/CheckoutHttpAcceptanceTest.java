package vn.covie.auth.acceptance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@org.springframework.boot.test.context.SpringBootTest(properties = "covie.outbox.max-events-per-poll=0")
class CheckoutHttpAcceptanceTest extends AuthAcceptanceSupport {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired vn.covie.merchants.persistence.MerchantMembershipRepository merchantMemberships;
    @Autowired vn.covie.outbox.application.OutboxProcessor processor;

    @ParameterizedTest
    @ValueSource(strings = {"SUSPENDED", "HIDDEN"})
    void nonTradingMerchantCanStillReadConfirmAndProcessAnExistingOrder(String state) throws Exception {
        var buyer = activeUser("Historical order buyer");
        var seller = activeUser("Historical order seller");
        UUID offer = offer();
        UUID merchant = jdbc.queryForObject("select ml.merchant_profile_id from location_variants lv join merchant_locations ml on ml.id=lv.merchant_location_id where lv.id=?", UUID.class, offer);
        merchantMemberships.saveAndFlush(vn.covie.merchants.model.MerchantMembershipEntity.active(UUID.randomUUID(), merchant,
                seller.getId(), vn.covie.merchants.model.MerchantRole.OWNER, Instant.now()));
        String buyerBearer = "Bearer " + issuer.issue(buyer.getId(), device()).accessToken().value();
        String sellerBearer = "Bearer " + issuer.issue(seller.getId(), device()).accessToken().value();
        String previewBody = mapper.writeValueAsString(java.util.Map.of("source", java.util.Map.of("type", "DIRECT"),
                "items", java.util.List.of(java.util.Map.of("locationVariantId", offer, "quantity", 1)),
                "fulfillmentMethod", "PICKUP", "paymentMethod", "COD"));
        var preview = mvc.perform(post("/api/v1/checkouts/preview").header("Authorization", buyerBearer)
                .contentType(MediaType.APPLICATION_JSON).content(previewBody)).andExpect(status().isOk()).andReturn();
        var committed = mvc.perform(post("/api/v1/checkouts/commit").header("Authorization", buyerBearer)
                .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("checkoutToken", mapper.readTree(preview.getResponse().getContentAsString()).path("checkoutToken").asText()))))
                .andExpect(status().isCreated()).andReturn();
        String order = mapper.readTree(committed.getResponse().getContentAsString()).path("id").asText();
        if (state.equals("SUSPENDED")) jdbc.update("update merchant_profiles set merchant_status='SUSPENDED' where id=?", merchant);
        else jdbc.update("update merchant_profiles set moderation_status='HIDDEN' where id=?", merchant);
        mvc.perform(post("/api/v1/checkouts/preview").header("Authorization", buyerBearer)
                .contentType(MediaType.APPLICATION_JSON).content(previewBody)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/orders/{orderId}", order).header("Authorization", sellerBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PENDING_CONFIRMATION"));
        mvc.perform(post("/api/v1/orders/{orderId}/confirm", order).header("Authorization", sellerBearer)
                .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/api/v1/orders/{orderId}/mark-ready-for-pickup", order).header("Authorization", sellerBearer)
                .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders/{orderId}", order).header("Authorization", sellerBearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("READY_FOR_PICKUP"));
    }

    @org.junit.jupiter.api.RepeatedTest(3)
    void twoActualJwtBuyersOfLastTrackedUnitCreateOnlyOneOrder() throws Exception {
        var firstBuyer=activeUser("Last unit buyer one"); var secondBuyer=activeUser("Last unit buyer two");
        UUID offer=offer();
        jdbc.update("update location_variants set inventory_mode='TRACKED' where id=?",offer);
        jdbc.update("insert into inventory_balances(location_variant_id,stock_on_hand,reserved_quantity,updated_at,version) values (?,1,0,?,0)",offer,Timestamp.from(Instant.now()));
        String request="""
                {"source":{"type":"DIRECT"},"items":[{"locationVariantId":"%s","quantity":1}],
                 "fulfillmentMethod":"PICKUP","paymentMethod":"COD"}
                """.formatted(offer);
        var bearers=new java.util.ArrayList<String>(); var bodies=new java.util.ArrayList<String>();
        for(UUID user:java.util.List.of(firstBuyer.getId(),secondBuyer.getId())) {
            String bearer="Bearer "+issuer.issue(user,device()).accessToken().value();
            bearers.add(bearer);
            var preview=mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer)
                    .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn();
            bodies.add(mapper.writeValueAsString(java.util.Map.of("checkoutToken",
                    mapper.readTree(preview.getResponse().getContentAsString()).path("checkoutToken").asText())));
        }
        var start=new java.util.concurrent.CyclicBarrier(2);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var results=new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for(int index=0;index<2;index++) {
                final int actorIndex=index;
                results.add(workers.submit(()-> {
                    start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                    return mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearers.get(actorIndex))
                            .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                            .content(bodies.get(actorIndex))).andReturn().getResponse().getStatus();
                }));
            }
            assertThat(java.util.List.of(results.get(0).get(15,java.util.concurrent.TimeUnit.SECONDS),
                    results.get(1).get(15,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id in (?,?)",Long.class,
                firstBuyer.getId(),secondBuyer.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances where location_variant_id=?",Long.class,offer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select stock_on_hand from inventory_balances where location_variant_id=?",Long.class,offer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_reservations where location_variant_id=? and reservation_status='ACTIVE'",Long.class,offer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where location_variant_id=? and movement_type='RESERVE'",Long.class,offer)).isEqualTo(1);
        UUID order = jdbc.queryForObject("select id from orders where buyer_user_id in (?,?)", UUID.class, firstBuyer.getId(), secondBuyer.getId());
        UUID winner = jdbc.queryForObject("select buyer_user_id from orders where id=?", UUID.class, order);
        UUID merchant = jdbc.queryForObject("select merchant_profile_id from orders where id=?", UUID.class, order);
        var seller = activeUser("Tracked pickup seller");
        merchantMemberships.saveAndFlush(vn.covie.merchants.model.MerchantMembershipEntity.active(UUID.randomUUID(), merchant,
                seller.getId(), vn.covie.merchants.model.MerchantRole.OWNER, Instant.now()));
        String sellerBearer = "Bearer " + issuer.issue(seller.getId(), device()).accessToken().value();
        String buyerBearer = bearers.get(winner.equals(firstBuyer.getId()) ? 0 : 1);
        String route = "/api/v1/orders/" + order;
        for (String action : java.util.List.of("confirm", "mark-ready-for-pickup"))
            mvc.perform(post(route + "/" + action).header("Authorization", sellerBearer)
                    .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
        String completionKey = UUID.randomUUID().toString();
        for (int repeat = 0; repeat < 2; repeat++)
            mvc.perform(post(route + "/confirm-received").header("Authorization", buyerBearer)
                    .header("Idempotency-Key", completionKey)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("select stock_on_hand from inventory_balances where location_variant_id=?", Long.class, offer)).isZero();
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances where location_variant_id=?", Long.class, offer)).isZero();
        assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, order)).isEqualTo("COMMITTED");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=? and movement_type='SALE'", Long.class, order)).isEqualTo(1);
        for (int attempt = 0; attempt < 100; attempt++) {
            if (jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and status<>'PUBLISHED'", Long.class, order) == 0) break;
            processor.processOne();
        }
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and status<>'PUBLISHED'", Long.class, order)).isZero();
        assertThat(jdbc.queryForObject("select completed_order_count from user_reputation_stats where user_id=?", Long.class, winner)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select completed_order_count from merchant_reputation_stats where merchant_profile_id=?", Long.class, merchant)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UPDATE", "DELETE"})
    void cartAddAgainstMutationHasOnlySerializableOutcomes(String operation) throws Exception {
        for(int iteration=0;iteration<5;iteration++) {
            var buyer=activeUser("Mixed cart writer");
            String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
            String body=mapper.writeValueAsString(java.util.Map.of("locationVariantId",offer(),"quantity",1));
            var added=mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn();
            var cart=mapper.readTree(added.getResponse().getContentAsString());
            String path="/api/v1/me/carts/"+cart.path("id").asText()+"/items/"+cart.path("items").get(0).path("id").asText();
            String etag=added.getResponse().getHeader("ETag");
            var start=new java.util.concurrent.CyclicBarrier(2);
            int addStatus,mutationStatus;
            try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var add=workers.submit(()-> {
                    start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                    return mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                            .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
                });
                var mutation=workers.submit(()-> {
                    start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                    var request=operation.equals("UPDATE") ? patch(path).contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}") : delete(path);
                    return mvc.perform(request.header("Authorization",bearer).header("If-Match",etag))
                            .andReturn().getResponse().getStatus();
                });
                addStatus=add.get(15,java.util.concurrent.TimeUnit.SECONDS);
                mutationStatus=mutation.get(15,java.util.concurrent.TimeUnit.SECONDS);
            }
            assertThat(addStatus).isIn(201,409);
            assertThat(mutationStatus).isIn(operation.equals("UPDATE") ? 200 : 204,409);
            long quantity=jdbc.queryForObject("select coalesce(sum(i.quantity),0) from cart_items i join carts c on c.id=i.cart_id where c.user_id=? and c.cart_status='ACTIVE'",Long.class,buyer.getId());
            if(mutationStatus==409) assertThat(quantity).isEqualTo(addStatus==201 ? 2L : 1L);
            else if(operation.equals("UPDATE")) {
                if(addStatus==201) assertThat(quantity).isIn(5L,6L); else assertThat(quantity).isEqualTo(5L);
            } else {
                if(addStatus==201) assertThat(quantity).isIn(0L,1L); else assertThat(quantity).isZero();
            }
            assertThat(jdbc.queryForObject("select count(*) from cart_items i join carts c on c.id=i.cart_id where c.user_id=? and c.cart_status='ABANDONED'",Long.class,buyer.getId())).isZero();
        }
    }

    @org.junit.jupiter.api.RepeatedTest(5)
    void simultaneousCartAddAndCheckoutPreserveAllSuccessfulQuantities() throws Exception {
        var buyer=activeUser("Mixed checkout buyer");
        String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        String addBody=mapper.writeValueAsString(java.util.Map.of("locationVariantId",offer(),"quantity",1));
        var added=mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(addBody)).andExpect(status().isCreated()).andReturn();
        String cartId=mapper.readTree(added.getResponse().getContentAsString()).path("id").asText();
        String previewBody="""
                {"source":{"type":"CART","cartId":"%s"},"fulfillmentMethod":"PICKUP","paymentMethod":"COD"}
                """.formatted(cartId);
        var preview=mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(previewBody)).andExpect(status().isOk()).andReturn();
        String commitBody=mapper.writeValueAsString(java.util.Map.of("checkoutToken",
                mapper.readTree(preview.getResponse().getContentAsString()).path("checkoutToken").asText()));
        var start=new java.util.concurrent.CyclicBarrier(2);
        int addStatus,commitStatus;
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var add=workers.submit(()-> {
                start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(addBody)).andReturn().getResponse().getStatus();
            });
            var commit=workers.submit(()-> {
                start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearer)
                        .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content(commitBody)).andReturn().getResponse().getStatus();
            });
            addStatus=add.get(15,java.util.concurrent.TimeUnit.SECONDS);
            commitStatus=commit.get(15,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertThat(addStatus).isIn(201,409);
        assertThat(commitStatus).isIn(201,409);
        long ordered=jdbc.queryForObject("select coalesce(sum(i.quantity),0) from order_items i join orders o on o.id=i.order_id where o.buyer_user_id=?",Long.class,buyer.getId());
        long remaining=jdbc.queryForObject("select coalesce(sum(i.quantity),0) from cart_items i join carts c on c.id=i.cart_id where c.user_id=? and c.cart_status='ACTIVE'",Long.class,buyer.getId());
        assertThat(ordered+remaining).isEqualTo(addStatus==201 ? 2L : 1L);
        assertThat(jdbc.queryForObject("select count(*) from cart_items i join carts c on c.id=i.cart_id where c.user_id=? and c.cart_status='CHECKED_OUT'",Long.class,buyer.getId())).isZero();
    }

    @Test void serviceCartRejectsQuantityChangesAndDuplicateAddsWithoutChangingLine() throws Exception {
        var buyer=activeUser("Service cart buyer");
        String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        UUID offer=offer();
        UUID item=jdbc.queryForObject("select v.catalog_item_id from variants v join location_variants lv on lv.variant_id=v.id where lv.id=?",UUID.class,offer);
        jdbc.update("update categories set catalog_type='SERVICE' where id=(select primary_category_id from catalog_items where id=?)",item);
        jdbc.update("update catalog_items set item_type='SERVICE' where id=?",item);
        String body=mapper.writeValueAsString(java.util.Map.of("locationVariantId",offer,"quantity",1));
        mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("locationVariantId",offer,"quantity",2))))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CART_ITEM_INVALID"));
        assertThat(jdbc.queryForObject("select count(*) from carts where user_id=?",Long.class,buyer.getId())).isZero();
        var added=mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.cartType").value("SERVICE")).andReturn();
        var cart=mapper.readTree(added.getResponse().getContentAsString());
        String path="/api/v1/me/carts/"+cart.path("id").asText();
        String itemPath=path+"/items/"+cart.path("items").get(0).path("id").asText();
        String etag=added.getResponse().getHeader("ETag");
        mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnprocessableEntity());
        mvc.perform(patch(itemPath).header("Authorization",bearer).header("If-Match",etag)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(path).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(header().string("ETag",etag)).andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].quantity").value(1));
        mvc.perform(delete(itemPath).header("Authorization",bearer).header("If-Match",etag))
                .andExpect(status().isNoContent());
        mvc.perform(get(path).header("Authorization",bearer)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentCartAddsPreserveEverySuccessfulQuantity(boolean existingCart) throws Exception {
        var buyer=activeUser("Concurrent cart buyer");
        String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        UUID offer=offer();
        String body=mapper.writeValueAsString(java.util.Map.of("locationVariantId",offer,"quantity",1));
        if(existingCart) mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated());
        var start=new java.util.concurrent.CyclicBarrier(2);
        long successes;
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> add=()-> {
                start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse().getStatus();
            };
            var first=workers.submit(add); var second=workers.submit(add);
            var statuses=java.util.List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(statuses).containsExactly(201,201);
            successes=statuses.stream().filter(status->status==201).count();
        }
        assertThat(jdbc.queryForObject("select count(*) from carts where user_id=? and cart_status='ACTIVE'",Long.class,buyer.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select quantity from cart_items where location_variant_id=?",Long.class,offer))
                .isEqualTo(successes+(existingCart ? 1L : 0L));
    }

    @Test void cartCheckoutRejectsChangedCartAndClearsOnlyAfterSuccessfulCommit() throws Exception {
        var buyer=activeUser("Cart checkout buyer");
        String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        var added=mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("locationVariantId",offer(),"quantity",1))))
                .andExpect(status().isCreated()).andReturn();
        var cart=mapper.readTree(added.getResponse().getContentAsString());
        String cartId=cart.path("id").asText();
        String request="""
                {"source":{"type":"CART","cartId":"%s"},"fulfillmentMethod":"PICKUP","paymentMethod":"COD"}
                """.formatted(cartId);
        var preview=mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn();
        String token=mapper.readTree(preview.getResponse().getContentAsString()).path("checkoutToken").asText();
        mvc.perform(patch("/api/v1/me/carts/"+cartId+"/items/"+cart.path("items").get(0).path("id").asText())
                .header("Authorization",bearer).header("If-Match",added.getResponse().getHeader("ETag"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearer)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("checkoutToken",token))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CHECKOUT_CHANGED"));
        mvc.perform(get("/api/v1/me/carts/"+cartId).header("Authorization",bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=?",Long.class,buyer.getId())).isZero();
        var fresh=mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn();
        String freshToken=mapper.readTree(fresh.getResponse().getContentAsString()).path("checkoutToken").asText();
        mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearer)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("checkoutToken",freshToken))))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select cart_status from carts where id=?",String.class,UUID.fromString(cartId))).isEqualTo("CHECKED_OUT");
        assertThat(jdbc.queryForObject("select count(*) from cart_items where cart_id=?",Long.class,UUID.fromString(cartId))).isZero();
        assertThat(jdbc.queryForObject("select subtotal from orders where buyer_user_id=?",Long.class,buyer.getId())).isEqualTo(20000L);
    }

    @Test void cartOwnershipAndVersionFailuresLeaveQuantityUnchanged() throws Exception {
        var buyer=activeUser("Versioned cart buyer"); var other=activeUser("Cart outsider");
        String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        String outsider="Bearer "+issuer.issue(other.getId(),device()).accessToken().value();
        var added=mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("locationVariantId",offer(),"quantity",1))))
                .andExpect(status().isCreated()).andReturn();
        var cart=mapper.readTree(added.getResponse().getContentAsString());
        String path="/api/v1/me/carts/"+cart.path("id").asText();
        String itemPath=path+"/items/"+cart.path("items").get(0).path("id").asText();
        String etag=added.getResponse().getHeader("ETag");
        mvc.perform(get(path).header("Authorization",outsider)).andExpect(status().isNotFound());
        mvc.perform(patch(itemPath).header("Authorization",outsider).header("If-Match",etag)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete(itemPath).header("Authorization",outsider).header("If-Match",etag))
                .andExpect(status().isNotFound());
        mvc.perform(patch(itemPath).header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isPreconditionRequired());
        mvc.perform(patch(itemPath).header("Authorization",bearer).header("If-Match","\"v999\"")
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isConflict());
        mvc.perform(patch(itemPath).header("Authorization",bearer).header("If-Match",etag)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":0}"))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(get(path).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(header().string("ETag",etag)).andExpect(jsonPath("$.items[0].quantity").value(1));
        mvc.perform(patch(itemPath).header("Authorization",bearer).header("If-Match",etag)
                .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].quantity").value(2));
        mvc.perform(delete(itemPath).header("Authorization",bearer).header("If-Match",etag))
                .andExpect(status().isConflict());
        mvc.perform(get(path).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test void removingOneOfTwoCartItemsKeepsCartActiveUntilLastItemIsRemoved() throws Exception {
        var buyer=activeUser("Cart buyer");
        String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        UUID first=offer(),second=UUID.randomUUID(),variant=UUID.randomUUID();
        jdbc.update("insert into variants(id,catalog_item_id,sku,variant_name,is_default,variant_status,sort_order,created_at,updated_at,version) select ?,catalog_item_id,'SECOND','Second',false,'ACTIVE',1,created_at,updated_at,0 from variants where id=(select variant_id from location_variants where id=?)",variant,first);
        jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at,version) select ?,merchant_location_id,?,price,currency_code,true,'UNTRACKED',0,created_at,updated_at,0 from location_variants where id=?",second,variant,first);
        com.fasterxml.jackson.databind.JsonNode cart=null;
        for(UUID id:java.util.List.of(first,second)) {
            var added=mvc.perform(post("/api/v1/me/carts/items").header("Authorization",bearer)
                    .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("locationVariantId",id,"quantity",1))))
                    .andExpect(status().isCreated()).andReturn();
            cart=mapper.readTree(added.getResponse().getContentAsString());
        }
        assertThat(cart.path("items").size()).isEqualTo(2);
        String path="/api/v1/me/carts/"+cart.path("id").asText();
        mvc.perform(delete(path+"/items/"+cart.path("items").get(0).path("id").asText())
                .header("Authorization",bearer).header("If-Match","\"v"+cart.path("version").asLong()+"\""))
                .andExpect(status().isNoContent());
        var remaining=mvc.perform(get(path).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1)).andReturn();
        var last=mapper.readTree(remaining.getResponse().getContentAsString());
        mvc.perform(delete(path+"/items/"+last.path("items").get(0).path("id").asText())
                .header("Authorization",bearer).header("If-Match",remaining.getResponse().getHeader("ETag")))
                .andExpect(status().isNoContent());
        mvc.perform(get(path).header("Authorization",bearer)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("select cart_status from carts where id=?",String.class,
                UUID.fromString(cart.path("id").asText()))).isEqualTo("ABANDONED");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({"FIXED,false", "NEGOTIATED,false", "FIXED,true", "NEGOTIATED,true"})
    void deliveryHttpRequiresFreshPreviewAfterShippingChanges(String mode, boolean tracked) throws Exception {
        var buyer=activeUser("Delivery buyer");
        String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        UUID offer=offer(),address=UUID.randomUUID(),policy=UUID.randomUUID();
        UUID location=jdbc.queryForObject("select merchant_location_id from location_variants where id=?",UUID.class,offer);
        Timestamp now=Timestamp.from(Instant.now());
        if (tracked) {
            jdbc.update("update location_variants set inventory_mode='TRACKED' where id=?", offer);
            jdbc.update("insert into inventory_balances(location_variant_id,stock_on_hand,reserved_quantity,updated_at,version) values (?,1,0,?,0)", offer, now);
        }
        jdbc.update("update merchant_locations set seller_delivery_enabled=true where id=?",location);
        jdbc.update("insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,area_enabled,coverage_status,created_at,updated_at,version) values (?,?,'PRODUCT_DELIVERY',true,5000,false,'ACTIVE',?,?,0)",UUID.randomUUID(),location,now,now);
        jdbc.update("insert into user_addresses(id,user_id,label,recipient_name,phone,address_text,geo_point,pin_source,is_default,status,created_at,updated_at,version) values (?,?,'Home','Buyer','0900000001','Street',ST_GeogFromText('SRID=4326;POINT(106.705 10.805)'),'GPS',true,'ACTIVE',?,?,0)",address,buyer.getId(),now,now);
        jdbc.update("insert into shipping_policies(id,merchant_location_id,mode,base_fee,currency_code,rule_match_mode,policy_status,created_at,updated_at,version) values (?,?,'FREE',null,'VND',null,'ACTIVE',?,?,0)",policy,location,now,now);
        String request="""
                {"sourceType":"DIRECT","items":[{"locationVariantId":"%s","quantity":1}],
                 "fulfillmentMethod":"SELLER_DELIVERY","addressId":"%s","paymentMethod":"COD"}
                """.formatted(offer,address);
        var preview=mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk())
                .andExpect(jsonPath("$.shippingFee").value(0)).andExpect(jsonPath("$.total").value(10000)).andReturn();
        String oldToken=mapper.readTree(preview.getResponse().getContentAsString()).path("checkoutToken").asText();
        jdbc.update("update shipping_policies set mode=?,base_fee=?,version=version+1 where id=?",
                mode,mode.equals("FIXED") ? 5000L : null,policy);
        mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearer)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("checkoutToken",oldToken))))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CHECKOUT_CHANGED"))
                .andExpect(jsonPath("$.error.details.changes[0].type").value("SHIPPING_CHANGED"));
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=?",Long.class,buyer.getId())).isZero();
        var fresh=mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn();
        var freshJson=mapper.readTree(fresh.getResponse().getContentAsString());
        if (mode.equals("FIXED")) {
            assertThat(freshJson.path("shippingFee").asLong()).isEqualTo(5000);
            assertThat(freshJson.path("total").asLong()).isEqualTo(15000);
        } else {
            assertThat(freshJson.hasNonNull("shippingFee")).isFalse();
            assertThat(freshJson.hasNonNull("total")).isFalse();
            assertThat(freshJson.path("knownTotal").asLong()).isEqualTo(10000);
        }
        mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearer)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(java.util.Map.of("checkoutToken",freshJson.path("checkoutToken").asText()))))
                .andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("select shipping_fee_type from orders where buyer_user_id=?",String.class,buyer.getId())).isEqualTo(mode);
        assertThat(jdbc.queryForObject("select shipping_fee from orders where buyer_user_id=?",Long.class,buyer.getId()))
                .isEqualTo(mode.equals("FIXED") ? 5000L : null);
        assertThat(jdbc.queryForObject("select total_amount from orders where buyer_user_id=?",Long.class,buyer.getId()))
                .isEqualTo(mode.equals("FIXED") ? 15000L : null);
        {
            UUID order = jdbc.queryForObject("select id from orders where buyer_user_id=?", UUID.class, buyer.getId());
            UUID merchant = jdbc.queryForObject("select merchant_profile_id from orders where id=?", UUID.class, order);
            var seller = activeUser("Fixed delivery seller");
            merchantMemberships.saveAndFlush(vn.covie.merchants.model.MerchantMembershipEntity.active(UUID.randomUUID(), merchant,
                    seller.getId(), vn.covie.merchants.model.MerchantRole.OWNER, Instant.now()));
            String sellerBearer = "Bearer " + issuer.issue(seller.getId(), device()).accessToken().value();
            String route = "/api/v1/orders/" + order;
            for (String action : java.util.List.of("confirm", "mark-out-for-delivery", "mark-delivered"))
                mvc.perform(post(route + "/" + action).header("Authorization", sellerBearer)
                        .header("Idempotency-Key", UUID.randomUUID().toString())).andExpect(status().isOk());
            if (tracked) {
                assertThat(jdbc.queryForObject("select stock_on_hand from inventory_balances where location_variant_id=?", Long.class, offer)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances where location_variant_id=?", Long.class, offer)).isEqualTo(1);
                assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, order)).isEqualTo("ACTIVE");
            }
            String completionKey = UUID.randomUUID().toString();
            for (int repeat = 0; repeat < 2; repeat++)
                mvc.perform(post(route + "/confirm-received").header("Authorization", bearer)
                        .header("Idempotency-Key", completionKey)).andExpect(status().isOk());
            mvc.perform(get(route).header("Authorization", bearer)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"));
            for (int attempt = 0; attempt < 100; attempt++) {
                if (jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and status<>'PUBLISHED'", Long.class, order) == 0) break;
                processor.processOne();
            }
            assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and status<>'PUBLISHED'", Long.class, order)).isZero();
            assertThat(jdbc.queryForObject("select completed_order_count from user_reputation_stats where user_id=?", Long.class, buyer.getId())).isEqualTo(1);
            assertThat(jdbc.queryForObject("select completed_order_count from merchant_reputation_stats where merchant_profile_id=?", Long.class, merchant)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=? and event_type='BUYER_CONFIRMED_RECEIVED'", Long.class, order)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select shipping_fee from orders where id=?", Long.class, order)).isEqualTo(mode.equals("FIXED") ? 5000L : null);
            assertThat(jdbc.queryForObject("select total_amount from orders where id=?", Long.class, order)).isEqualTo(mode.equals("FIXED") ? 15000L : null);
            if (tracked) {
                assertThat(jdbc.queryForObject("select stock_on_hand from inventory_balances where location_variant_id=?", Long.class, offer)).isZero();
                assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances where location_variant_id=?", Long.class, offer)).isZero();
                assertThat(jdbc.queryForObject("select reservation_status from inventory_reservations where order_id=?", String.class, order)).isEqualTo("COMMITTED");
                assertThat(jdbc.queryForObject("select count(*) from inventory_movements where order_id=? and movement_type='SALE'", Long.class, order)).isEqualTo(1);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentCommitsConsumeCheckoutOnceThroughActualJwtHttp(boolean sameKey) throws Exception {
        var buyer=activeUser("Concurrent checkout buyer");
        String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        UUID trackedOffer=offer();
        jdbc.update("update location_variants set inventory_mode='TRACKED' where id=?",trackedOffer);
        jdbc.update("insert into inventory_balances(location_variant_id,stock_on_hand,reserved_quantity,updated_at,version) values (?,1,0,?,0)",
                trackedOffer,Timestamp.from(Instant.now()));
        String request="""
                {"sourceType":"DIRECT","items":[{"locationVariantId":"%s","quantity":1}],
                 "fulfillmentMethod":"PICKUP","paymentMethod":"COD"}
                """.formatted(trackedOffer);
        var preview=mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn();
        String token=mapper.readTree(preview.getResponse().getContentAsString()).path("checkoutToken").asText();
        String body=mapper.writeValueAsString(java.util.Map.of("checkoutToken",token));
        String sharedKey=UUID.randomUUID().toString();
        var start=new java.util.concurrent.CyclicBarrier(2);
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<org.springframework.mock.web.MockHttpServletResponse> commit=()-> {
                start.await(5,java.util.concurrent.TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearer)
                        .header("Idempotency-Key",sameKey ? sharedKey : UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andReturn().getResponse();
            };
            var first=workers.submit(commit); var second=workers.submit(commit);
            var responses=java.util.List.of(first.get(15,java.util.concurrent.TimeUnit.SECONDS),
                    second.get(15,java.util.concurrent.TimeUnit.SECONDS));
            if (sameKey) {
                assertThat(responses.stream().map(response->response.getStatus()).toList()).containsExactly(201,201);
                assertThat(mapper.readTree(responses.get(0).getContentAsString()).path("id").asText())
                        .isEqualTo(mapper.readTree(responses.get(1).getContentAsString()).path("id").asText());
            } else {
                assertThat(responses.stream().map(response->response.getStatus()).toList())
                        .containsExactlyInAnyOrder(201,409);
                var rejected=responses.stream().filter(response->response.getStatus()==409).findFirst().orElseThrow();
                assertThat(mapper.readTree(rejected.getContentAsString()).path("error").path("code").asText())
                        .isEqualTo("CHECKOUT_SESSION_CONSUMED");
            }
        }
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=?",Long.class,buyer.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from checkout_sessions where user_id=? and consumed_at is not null",
                Long.class,buyer.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select stock_on_hand from inventory_balances where location_variant_id=?",Long.class,trackedOffer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select reserved_quantity from inventory_balances where location_variant_id=?",Long.class,trackedOffer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_reservations where location_variant_id=? and reservation_status='ACTIVE'",Long.class,trackedOffer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where location_variant_id=? and movement_type='RESERVE'",Long.class,trackedOffer)).isEqualTo(1);
    }

    @Test void actualJwtCheckoutRejectsForeignTokenAndReplaysOnlyOneOrder() throws Exception {
        var buyer=activeUser("Checkout buyer"); var other=activeUser("Checkout outsider");
        String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        String outsider="Bearer "+issuer.issue(other.getId(),device()).accessToken().value();
        UUID offer=offer();
        String request="""
                {"sourceType":"DIRECT","items":[{"locationVariantId":"%s","quantity":1}],
                 "fulfillmentMethod":"PICKUP","paymentMethod":"COD"}
                """.formatted(offer);
        mvc.perform(post("/api/v1/checkouts/preview").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code").value("CSRF_INVALID"));
        var preview=mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk()).andReturn();
        String token=mapper.readTree(preview.getResponse().getContentAsString()).path("checkoutToken").asText();
        assertThat(token).isNotBlank();
        String body=mapper.writeValueAsString(java.util.Map.of("checkoutToken",token));
        mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",outsider)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CHECKOUT_SESSION_INVALID"));
        String key=UUID.randomUUID().toString();
        var first=mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearer)
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn();
        String id=mapper.readTree(first.getResponse().getContentAsString()).path("id").asText();
        mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearer)
                .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearer)
                .header("Idempotency-Key",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("CHECKOUT_SESSION_CONSUMED"));
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=?",Long.class,buyer.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from checkout_sessions where user_id=? and consumed_at is not null",
                Long.class,buyer.getId())).isEqualTo(1);
    }

    @Test void serviceHttpCheckoutCreatesOneServiceFulfillmentAndReplaysOneOrder() throws Exception {
        var buyer=activeUser("Service checkout buyer");
        String bearer="Bearer "+issuer.issue(buyer.getId(),device()).accessToken().value();
        UUID offer=offer(),address=UUID.randomUUID();
        UUID location=jdbc.queryForObject("select merchant_location_id from location_variants where id=?",UUID.class,offer);
        UUID item=jdbc.queryForObject("select v.catalog_item_id from variants v join location_variants lv on lv.variant_id=v.id where lv.id=?",UUID.class,offer);
        Timestamp now=Timestamp.from(Instant.now());
        jdbc.update("update categories set catalog_type='SERVICE' where id=(select primary_category_id from catalog_items where id=?)",item);
        jdbc.update("update catalog_items set item_type='SERVICE' where id=?",item);
        jdbc.update("update merchant_locations set service_enabled=true where id=?",location);
        jdbc.update("insert into delivery_coverages(id,merchant_location_id,coverage_type,radius_enabled,radius_meters,area_enabled,coverage_status,created_at,updated_at,version) values (?,?,'SERVICE_AREA',true,5000,false,'ACTIVE',?,?,0)",UUID.randomUUID(),location,now,now);
        jdbc.update("insert into user_addresses(id,user_id,label,recipient_name,phone,address_text,geo_point,pin_source,is_default,status,created_at,updated_at,version) values (?,?,'Home','Buyer','0900000001','Street',ST_GeogFromText('SRID=4326;POINT(106.705 10.805)'),'GPS',true,'ACTIVE',?,?,0)",address,buyer.getId(),now,now);
        String request=mapper.writeValueAsString(java.util.Map.of("source",java.util.Map.of("type","DIRECT"),
                "items",java.util.List.of(java.util.Map.of("locationVariantId",offer,"quantity",1)),
                "fulfillmentMethod","SERVICE","addressId",address,"paymentMethod","COD"));
        var outsider=activeUser("Foreign service address");
        jdbc.update("update user_addresses set user_id=? where id=?",outsider.getId(),address);
        mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CHECKOUT_FULFILLMENT_NOT_SUPPORTED"));
        jdbc.update("update user_addresses set user_id=?,status='DELETED' where id=?",buyer.getId(),address);
        mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnprocessableEntity());
        jdbc.update("update user_addresses set status='ACTIVE' where id=?",address);
        jdbc.update("update delivery_coverages set radius_meters=10 where merchant_location_id=?",location);
        mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("CHECKOUT_FULFILLMENT_NOT_SUPPORTED"));
        jdbc.update("update delivery_coverages set radius_meters=5000 where merchant_location_id=?",location);
        jdbc.update("update merchant_locations set service_enabled=false where id=?",location);
        mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("select count(*) from checkout_sessions where user_id=?",Long.class,buyer.getId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=?",Long.class,buyer.getId())).isZero();
        jdbc.update("update merchant_locations set service_enabled=true where id=?",location);
        var preview=mvc.perform(post("/api/v1/checkouts/preview").header("Authorization",bearer)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(10000)).andReturn();
        String token=mapper.readTree(preview.getResponse().getContentAsString()).path("checkoutToken").asText();
        String body=mapper.writeValueAsString(java.util.Map.of("checkoutToken",token)),key=UUID.randomUUID().toString();
        UUID order=null;
        for(int attempt=0;attempt<2;attempt++) {
            var response=mvc.perform(post("/api/v1/checkouts/commit").header("Authorization",bearer)
                    .header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated()).andReturn();
            UUID returned=UUID.fromString(mapper.readTree(response.getResponse().getContentAsString()).path("id").asText());
            if(order==null) order=returned; else assertThat(returned).isEqualTo(order);
        }
        assertThat(jdbc.queryForObject("select count(*) from orders where buyer_user_id=?",Long.class,buyer.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select order_type from orders where id=?",String.class,order)).isEqualTo("SERVICE");
        assertThat(jdbc.queryForObject("select shipping_fee_type from orders where id=?",String.class,order)).isEqualTo("NOT_APPLICABLE");
        assertThat(jdbc.queryForObject("select total_amount from orders where id=?",Long.class,order)).isEqualTo(10000);
        assertThat(jdbc.queryForObject("select service_address_id from service_fulfillments where order_id=?",UUID.class,order)).isEqualTo(address);
        assertThat(jdbc.queryForObject("select distance_meters from service_fulfillments where order_id=?",Integer.class,order)).isPositive();
        assertThat(jdbc.queryForObject("select count(*) from product_fulfillments where order_id=?",Long.class,order)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=?",Long.class,order)).isEqualTo(1);
        var seller=activeUser("Service provider");
        UUID merchant=jdbc.queryForObject("select merchant_profile_id from orders where id=?",UUID.class,order);
        merchantMemberships.saveAndFlush(vn.covie.merchants.model.MerchantMembershipEntity.active(UUID.randomUUID(),merchant,
                seller.getId(),vn.covie.merchants.model.MerchantRole.OWNER,Instant.now()));
        String sellerBearer="Bearer "+issuer.issue(seller.getId(),device()).accessToken().value();
        String route="/api/v1/orders/"+order;
        for(String action:java.util.List.of("confirm","mark-service-performed"))
            mvc.perform(post(route+"/"+action).header("Authorization",sellerBearer)
                    .header("Idempotency-Key",UUID.randomUUID().toString())).andExpect(status().isOk());
        String completionKey=UUID.randomUUID().toString();
        for(int attempt=0;attempt<2;attempt++) mvc.perform(post(route+"/confirm-completed").header("Authorization",bearer)
                .header("Idempotency-Key",completionKey)).andExpect(status().isOk());
        mvc.perform(get(route).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.items[0].reviewEligibility.eligible").value(true));
        for(int attempt=0;attempt<100;attempt++) {
            if(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and status<>'PUBLISHED'",Long.class,order)==0) break;
            processor.processOne();
        }
        assertThat(jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=? and status<>'PUBLISHED'",Long.class,order)).isZero();
        assertThat(jdbc.queryForObject("select completed_order_count from user_reputation_stats where user_id=?",Long.class,buyer.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select completed_order_count from merchant_reputation_stats where merchant_profile_id=?",Long.class,merchant)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from order_events where order_id=? and event_type='BUYER_CONFIRMED_SERVICE_COMPLETED'",Long.class,order)).isEqualTo(1);
        UUID orderItem=jdbc.queryForObject("select id from order_items where order_id=?",UUID.class,order);
        mvc.perform(post("/api/v1/me/reviews").header("Authorization",bearer).header("Idempotency-Key",UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(java.util.Map.of("orderItemId",orderItem,"rating",5))))
                .andExpect(status().isCreated());
        mvc.perform(get(route).header("Authorization",bearer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reviewEligibility.reason").value("ALREADY_REVIEWED"));
    }

    private UUID offer() {
        UUID merchant=UUID.randomUUID(),location=UUID.randomUUID(),category=UUID.randomUUID();
        UUID item=UUID.randomUUID(),variant=UUID.randomUUID(),offer=UUID.randomUUID();
        Timestamp now=Timestamp.from(Instant.now());
        jdbc.update("insert into merchant_profiles(id,name,slug,merchant_status,moderation_status,created_at,updated_at,version) values (?,? ,?,'ACTIVE','APPROVED',?,?,0)",merchant,"Shop","shop-"+merchant,now,now);
        jdbc.update("insert into merchant_locations(id,merchant_profile_id,name,address_text,geo_point,pin_source,timezone,location_status,pickup_enabled,seller_delivery_enabled,service_enabled,minimum_delivery_order,currency_code,created_at,updated_at,version) values (?,?,'Main','Street',ST_GeogFromText('SRID=4326;POINT(106.7 10.8)'),'GPS','Asia/Ho_Chi_Minh','ACTIVE',true,false,false,0,'VND',?,?,0)",location,merchant,now,now);
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?,'Product',?,'PRODUCT','ACTIVE',0,0)",category,"category-"+category);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at,version) values (?,?,'PRODUCT','STANDARD',?,'Item',?,'ACTIVE','APPROVED',?,?,0)",item,merchant,category,"item-"+item,now,now);
        jdbc.update("insert into variants(id,catalog_item_id,sku,variant_name,is_default,variant_status,sort_order,created_at,updated_at,version) values (?,?,'SKU','Default',true,'ACTIVE',0,?,?,0)",variant,item,now,now);
        jdbc.update("insert into location_variants(id,merchant_location_id,variant_id,price,currency_code,is_available,inventory_mode,preparation_time_minutes,created_at,updated_at,version) values (?,?,?,10000,'VND',true,'UNTRACKED',0,?,?,0)",offer,location,variant,now,now);
        return offer;
    }
}



