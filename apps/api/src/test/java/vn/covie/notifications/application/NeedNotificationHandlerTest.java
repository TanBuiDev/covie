package vn.covie.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.delivery.application.DeliveryCoverageEvaluator;
import vn.covie.location.application.LocationContext;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.needs.application.NeedLocationContextFactory;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.notifications.model.NotificationType;
import vn.covie.notifications.persistence.NeedAudienceRepository;
import vn.covie.outbox.application.OutboxEventContext;

class NeedNotificationHandlerTest {
    @Test
    void sendsOnePrivacySafeMatchNotificationWhenSameSellerIsEligibleThroughTwoLocations() {
        var needs=mock(NeedRequestRepository.class); var notifications=mock(NotificationService.class); var audience=mock(NeedAudienceRepository.class);
        var locations=mock(MerchantLocationRepository.class); var needLocation=mock(NeedLocationContextFactory.class); var coverage=mock(DeliveryCoverageEvaluator.class); var recipients=mock(MerchantNotificationRecipientResolver.class);
        UUID needId=UUID.randomUUID(), eventId=UUID.randomUUID(), merchant=UUID.randomUUID(), user=UUID.randomUUID(), first=UUID.randomUUID(), second=UUID.randomUUID();
        var need=mock(NeedRequestEntity.class); when(need.getId()).thenReturn(needId); when(need.getNeedType()).thenReturn(NeedType.PRODUCT); when(needs.findById(needId)).thenReturn(Optional.of(need)); when(needLocation.from(need)).thenReturn(mock(LocationContext.class));
        when(audience.findCandidateLocations(needId,100)).thenReturn(List.of(new NeedAudienceRepository.NeedAudienceLocation(merchant,first,1),new NeedAudienceRepository.NeedAudienceLocation(merchant,second,2)));
        when(locations.findById(first)).thenReturn(Optional.of(pickupLocation(first,merchant))); when(locations.findById(second)).thenReturn(Optional.of(pickupLocation(second,merchant)));
        when(recipients.processorsForLocation(merchant,first)).thenReturn(Set.of(user)); when(recipients.processorsForLocation(merchant,second)).thenReturn(Set.of(user));
        when(notifications.createFromOutbox(eq(eventId),eq(user),eq(NotificationType.NEED_MATCH_AVAILABLE),anyString(),anyString(),eq("NEED_REQUEST"),eq(needId),anyMap())).thenReturn(new NotificationCreationResult(UUID.randomUUID(),true));

        var signals=new NeedNotificationHandler(needs,notifications,audience,locations,needLocation,coverage,recipients,100).handle(new OutboxEventContext(eventId,"NEED_CREATED","NEED_REQUEST",needId,JsonNodeFactory.instance.objectNode(),Instant.now()));

        verify(notifications,times(1)).createFromOutbox(eq(eventId),eq(user),eq(NotificationType.NEED_MATCH_AVAILABLE),anyString(),anyString(),eq("NEED_REQUEST"),eq(needId),argThat(payload -> payload.keySet().containsAll(List.of("needRequestId","merchantLocationId")) && payload.keySet().stream().noneMatch(key -> key.matches(".*(?i)(address|latitude|longitude|geo).*"))));
        assertThat(signals).hasSize(2).allMatch(signal -> signal.userId().equals(user));
    }

    private static MerchantLocationEntity pickupLocation(UUID id, UUID merchant) {
        return MerchantLocationEntity.create(id,merchant,"Main","non-sensitive",null,new org.locationtech.jts.geom.GeometryFactory().createPoint(new org.locationtech.jts.geom.Coordinate(106.7,10.8)),vn.covie.merchants.model.PinSource.GPS,"UTC",true,false,false,null,"VND",Instant.now());
    }
}
