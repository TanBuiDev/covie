package vn.covie.needs.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.PrecisionModel;
import org.mockito.Mockito;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.location.application.LocationContext;
import vn.covie.location.application.LocationContextResolver;
import vn.covie.location.application.LocationResolutionSource;
import vn.covie.moderation.application.ModerationDecision;
import vn.covie.moderation.application.ModerationDecisionService;
import vn.covie.moderation.model.ContentModerationStatus;
import vn.covie.needs.api.dto.CreateNeedRequest;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.CategoryHierarchyRepository;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.moderation.ModerationCaseWriter;
import vn.covie.shared.moderation.ModerationTargetRef;
import vn.covie.shared.outbox.OutboxEventWriter;

class NeedCommandModerationTest {
    @Test
    void keepsNeedActiveButPendingAndOpensPreapprovalCase() {
        LocationContextResolver locations = Mockito.mock(LocationContextResolver.class);
        CategoryHierarchyRepository categories = Mockito.mock(CategoryHierarchyRepository.class);
        NeedRequestRepository needs = Mockito.mock(NeedRequestRepository.class);
        ModerationDecisionService moderation = Mockito.mock(ModerationDecisionService.class);
        ModerationCaseWriter cases = Mockito.mock(ModerationCaseWriter.class);
        UUID buyerId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        when(categories.supportsNeedType(categoryId, NeedType.PRODUCT)).thenReturn(true);
        when(locations.resolveForDiscovery(any(), any(), any())).thenReturn(new LocationContext(LocationResolutionSource.COORDINATES,
                new GeometryFactory(new PrecisionModel(), 4326).createPoint(new Coordinate(106.7, 10.7)), areaId, null, ZoneId.of("Asia/Ho_Chi_Minh")));
        when(needs.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(moderation.decide(any())).thenReturn(ModerationDecision.REQUIRE_MODERATION);
        NeedCommandService service = new NeedCommandService(Mockito.mock(CurrentActorProvider.class), locations, categories,
                new NeedLocationSnapshotFactory(new ObjectMapper()), needs, Mockito.mock(IdempotencyExecutor.class),
                Mockito.mock(OutboxEventWriter.class), moderation, cases, new UuidV7Generator(), () -> now);

        service.createInTransaction(buyerId, new CreateNeedRequest(NeedType.PRODUCT, categoryId, "Rice", "rice", null,
                1L, null, 10.7, 106.7, 1000, now.plusSeconds(3600)));

        NeedRequestEntity need = Mockito.mockingDetails(needs).getInvocations().stream()
                .filter(i -> i.getMethod().getName().equals("save")).findFirst()
                .map(i -> (NeedRequestEntity) i.getArgument(0)).orElseThrow();
        assertThat(need.getRequestStatus().name()).isEqualTo("ACTIVE");
        assertThat(need.getModerationStatus()).isEqualTo(ContentModerationStatus.PENDING);
        verify(cases).openPreApproval(new ModerationTargetRef(vn.covie.moderation.model.ModerationTargetType.NEED_REQUEST, need.getId()),
                "PUBLISH_REQUIRES_MODERATION");
    }
}
