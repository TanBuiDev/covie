package vn.covie.reviews.application;

import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.orders.security.OrderActorType;
import vn.covie.orders.persistence.OrderItemRepository;
import vn.covie.orders.persistence.OrderRepository;
import vn.covie.reviews.api.dto.CreateReviewRequest;
import vn.covie.reviews.api.dto.ReviewResponse;
import vn.covie.reviews.model.ReviewEntity;
import vn.covie.reviews.persistence.ReviewRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.outbox.OutboxEventWriter;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class ReviewService {
    private final CurrentActorProvider actors; private final OrderItemRepository orderItems; private final OrderRepository orders;
    private final ReviewRepository reviews; private final ReviewPolicyProvider policy; private final RatingProjectionService projections;
    private final OutboxEventWriter outbox; private final IdempotencyExecutor idempotency; private final UuidV7Generator ids; private final AppClock clock;
    public ReviewService(CurrentActorProvider actors, OrderItemRepository orderItems, OrderRepository orders, ReviewRepository reviews,
            ReviewPolicyProvider policy, RatingProjectionService projections, OutboxEventWriter outbox, IdempotencyExecutor idempotency,
            UuidV7Generator ids, AppClock clock) { this.actors=actors; this.orderItems=orderItems; this.orders=orders; this.reviews=reviews; this.policy=policy; this.projections=projections; this.outbox=outbox; this.idempotency=idempotency; this.ids=ids; this.clock=clock; }
    public ReviewResponse create(CreateReviewRequest request, String key) {
        UUID actorId = actors.currentActor().userId();
        return idempotency.execute(actorId, "review.create:" + request.orderItemId(), key, request, 201, ReviewResponse.class,
                () -> createInsideTransaction(actorId, request));
    }
    @Transactional
    ReviewResponse createInsideTransaction(UUID actorId, CreateReviewRequest request) {
        var item = orderItems.findById(request.orderItemId()).orElseThrow(ReviewService::notFound);
        var order = orders.lockById(item.getOrderId()).orElseThrow(ReviewService::notFound);
        if (!order.getBuyerUserId().equals(actorId)) throw notFound();
        var eligibility = ReviewEligibilityPolicy.evaluate(order, item, OrderActorType.BUYER,
                reviews.existsByOrderItemId(item.getId()), policy.creationEnabled());
        if (!eligibility.eligible()) throw switch (eligibility.reason()) {
            case "CREATION_DISABLED" -> error(ErrorCode.REVIEW_CREATION_DISABLED, HttpStatus.UNPROCESSABLE_ENTITY, "Review creation is disabled.");
            case "ORDER_NOT_COMPLETED" -> error(ErrorCode.REVIEW_ORDER_NOT_COMPLETED, HttpStatus.CONFLICT, "Order is not completed.");
            case "ALREADY_REVIEWED" -> error(ErrorCode.REVIEW_ALREADY_EXISTS, HttpStatus.CONFLICT, "Review already exists.");
            default -> notFound();
        };
        ReviewEntity review = ReviewEntity.active(ids.generate(), item.getId(), order.getId(), actorId, item.getCatalogItemId(), item.getVariantId(), order.getMerchantProfileId(), (short) request.rating(), request.comment(), clock.now());
        try { reviews.saveAndFlush(review); } catch (DataIntegrityViolationException exception) { throw error(ErrorCode.REVIEW_ALREADY_EXISTS, HttpStatus.CONFLICT, "Review already exists."); }
        projections.add(review);
        outbox.append(ReviewOutboxEvents.created(review.getId(), order.getId(), item.getId(), order.getMerchantProfileId()));
        return new ReviewResponse(review.getId(), review.getOrderItemId(), review.getRating(), review.getComment(), review.getReviewStatus().name(), review.getCreatedAt());
    }
    private static ApiException notFound() { return error(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."); }
    private static ApiException error(ErrorCode code, HttpStatus status, String message) { return new ApiException(code, status, message); }
}
