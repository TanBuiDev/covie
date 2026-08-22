package vn.covie.reviews.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.NullNode;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.notifications.model.NotificationType;
import vn.covie.notifications.persistence.NotificationRepository;
import vn.covie.orders.model.OrderEntity;
import vn.covie.orders.model.OrderItemEntity;
import vn.covie.orders.model.OrderShippingFeeType;
import vn.covie.orders.model.OrderType;
import vn.covie.orders.persistence.OrderItemRepository;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.outbox.application.OutboxProcessor;
import vn.covie.reviews.api.dto.CreateReviewRequest;
import vn.covie.shared.commerce.PaymentMethod;
import vn.covie.shared.outbox.OutboxEventRepository;
import vn.covie.shared.outbox.OutboxEventStatus;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class ReviewNotificationEndToEndIntegrationTest extends PostgresIntegrationTest {
    @Autowired ReviewService reviews; @Autowired OutboxProcessor processor; @Autowired OutboxEventRepository outbox; @Autowired NotificationRepository notifications;
    @Autowired UserRepository users; @Autowired MerchantProfileRepository merchants; @Autowired MerchantLocationRepository locations; @Autowired MerchantMembershipRepository memberships;
    @Autowired OrderRepository orders; @Autowired OrderItemRepository orderItems; @Autowired JdbcTemplate jdbc; @Autowired TransactionTemplate transactions;

    @Test
    void committedReviewOutboxCreatesMerchantNotificationAndPublishesEvent() {
        Fixture fixture=transactions.execute(status -> createReviewFixture());

        assertThat(processor.processOne()).isTrue();

        assertThat(outbox.findById(fixture.eventId()).orElseThrow().getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
        assertThat(notifications.findBySourceOutboxEventIdAndUserIdAndNotificationType(fixture.eventId(),fixture.owner(),NotificationType.REVIEW_RECEIVED)).isPresent();
    }

    private Fixture createReviewFixture() {
        Instant now=Instant.parse("2026-08-23T00:00:00Z"); UUID buyer=UUID.randomUUID(), merchant=UUID.randomUUID(), owner=UUID.randomUUID(), category=UUID.randomUUID(), catalog=UUID.randomUUID(), variant=UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(buyer,"buyer-"+buyer,now)); users.saveAndFlush(UserEntity.active(owner,"owner-"+owner,now)); merchants.saveAndFlush(MerchantProfileEntity.create(merchant,"Merchant","merchant-"+merchant,now)); memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),merchant,owner,MerchantRole.OWNER,now));
        UUID location=locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(),merchant,"Main","1 Street",10.8,106.7,now)).getId();
        jdbc.update("insert into categories(id,name,slug,catalog_type,status,sort_order,version) values (?, 'Food', ?, 'PRODUCT','ACTIVE',0,0)",category,"food-"+category);
        jdbc.update("insert into catalog_items(id,merchant_profile_id,item_type,listing_kind,primary_category_id,name,slug,listing_status,moderation_status,created_at,updated_at,version) values (?,?, 'PRODUCT','STANDARD',?,'Rice',?,'ACTIVE','APPROVED',?,?,0)",catalog,merchant,category,"rice-"+catalog,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
        jdbc.update("insert into variants(id,catalog_item_id,variant_name,is_default,variant_status,created_at,updated_at,version) values (?,?, 'Default',true,'ACTIVE',?,?,0)",variant,catalog,java.sql.Timestamp.from(now),java.sql.Timestamp.from(now));
        var order=OrderEntity.pending(UUID.randomUUID(),"CV"+UUID.randomUUID().toString().replace("-","").substring(0,20),OrderType.PRODUCT,buyer,merchant,location,PaymentMethod.COD,100,OrderShippingFeeType.FREE,0L,100,100L,"VND",now.plusSeconds(60),now); order.complete(now); orders.saveAndFlush(order);
        var item=orderItems.saveAndFlush(OrderItemEntity.create(UUID.randomUUID(),order.getId(),catalog,variant,null,CatalogItemType.PRODUCT,1,100,"VND",NullNode.getInstance(),now));
        UUID reviewId=reviews.createInsideTransaction(buyer,new CreateReviewRequest(item.getId(),5,"Excellent")).id();
        UUID eventId=outbox.findAll().stream().filter(event -> event.getAggregateId().equals(reviewId)).findFirst().orElseThrow().getId();
        return new Fixture(eventId,owner);
    }

    private record Fixture(UUID eventId, UUID owner) {}
}
