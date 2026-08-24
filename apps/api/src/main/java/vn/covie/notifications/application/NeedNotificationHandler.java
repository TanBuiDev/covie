package vn.covie.notifications.application;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vn.covie.delivery.application.DeliveryCoverageEvaluator;
import vn.covie.delivery.model.CoverageType;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.needs.application.NeedLocationContextFactory;
import vn.covie.needs.model.NeedRequestEntity;
import vn.covie.needs.model.NeedType;
import vn.covie.needs.persistence.NeedRequestRepository;
import vn.covie.notifications.model.NotificationType;
import vn.covie.notifications.persistence.NeedAudienceRepository;
import vn.covie.outbox.application.OutboxEventContext;
import vn.covie.outbox.application.OutboxEventHandler;
import vn.covie.realtime.SseSignal;

@Component
public class NeedNotificationHandler implements OutboxEventHandler {
    private static final Logger log = LoggerFactory.getLogger(NeedNotificationHandler.class);
    private final NeedRequestRepository needs; private final NotificationService notifications; private final NeedAudienceRepository audience;
    private final MerchantLocationRepository locations; private final NeedLocationContextFactory needLocation;
    private final DeliveryCoverageEvaluator coverage; private final MerchantNotificationRecipientResolver recipients; private final int maxLocations;
    public NeedNotificationHandler(NeedRequestRepository needs, NotificationService notifications, NeedAudienceRepository audience, MerchantLocationRepository locations, NeedLocationContextFactory needLocation, DeliveryCoverageEvaluator coverage, MerchantNotificationRecipientResolver recipients, @Value("${covie.notifications.need-match-max-locations:100}") int maxLocations) {
        this.needs=needs; this.notifications=notifications; this.audience=audience; this.locations=locations; this.needLocation=needLocation; this.coverage=coverage; this.recipients=recipients; this.maxLocations=maxLocations;
    }
    @Override public boolean supports(String type) { return Set.of("NEED_CREATED","NEED_RESPONSE_CREATED","NEED_RESPONSE_UPDATED","NEED_RESPONSE_WITHDRAWN","NEED_FULFILLED","NEED_EXPIRED","NEED_CANCELLED").contains(type); }
    @Override public java.util.List<SseSignal> handle(OutboxEventContext event) {
        NeedRequestEntity need=needs.findById(event.aggregateId()).orElseThrow();
        if ("NEED_CREATED".equals(event.eventType())) return notifyMatchingSellers(event, need);
        if ("NEED_CANCELLED".equals(event.eventType())) return java.util.List.of(new SseSignal(need.getBuyerUserId(),"sync-required",Map.of()));
        NotificationType type=switch(event.eventType()) {case "NEED_RESPONSE_CREATED"->NotificationType.NEED_RESPONSE_RECEIVED;case "NEED_RESPONSE_UPDATED"->NotificationType.NEED_RESPONSE_UPDATED;case "NEED_RESPONSE_WITHDRAWN"->NotificationType.NEED_RESPONSE_WITHDRAWN;case "NEED_FULFILLED"->NotificationType.NEED_FULFILLED;case "NEED_EXPIRED"->NotificationType.NEED_EXPIRED;default->throw new IllegalArgumentException();};
        var created=notifications.createFromOutbox(event.eventId(),need.getBuyerUserId(),type,"Need update","Your request has an update.","NEED_REQUEST",need.getId(),Map.of("needRequestId",need.getId().toString()));
        String signal=switch(event.eventType()) {case "NEED_RESPONSE_CREATED"->"need-response.created";case "NEED_RESPONSE_UPDATED"->"need-response.updated";case "NEED_RESPONSE_WITHDRAWN"->"need-response.withdrawn";case "NEED_FULFILLED"->"need-request.fulfilled";default->"sync-required";};
        var signals=new ArrayList<SseSignal>(); if(created.created()) signals.add(new SseSignal(need.getBuyerUserId(),"notification.created",Map.of("notificationId",created.notificationId().toString()))); signals.add(new SseSignal(need.getBuyerUserId(),signal,Map.of("needRequestId",need.getId().toString()))); return signals;
    }
    private java.util.List<SseSignal> notifyMatchingSellers(OutboxEventContext event, NeedRequestEntity need) {
        var locationContext=needLocation.from(need); var userLocations=new TreeMap<UUID,UUID>();
        for (var candidate:audience.findCandidateLocations(need.getId(),maxLocations)) {
            var location=locations.findById(candidate.merchantLocationId()).orElse(null); if(location==null) continue;
            boolean eligible=need.getNeedType()==NeedType.PRODUCT ? location.isPickupEnabled() || coverage.evaluate(location.getId(),CoverageType.PRODUCT_DELIVERY,locationContext).eligible() : coverage.evaluate(location.getId(),CoverageType.SERVICE_AREA,locationContext).eligible();
            if (eligible) for(UUID user:recipients.processorsForLocation(candidate.merchantProfileId(),location.getId())) userLocations.putIfAbsent(user,location.getId());
        }
        if (userLocations.size() > 500) {
            log.warn("needNotificationAudienceCapped=true needRequestId={} recipientCount={} cap=500", need.getId(), userLocations.size());
        }
        var signals=new ArrayList<SseSignal>(); int sent=0;
        for(var recipient:userLocations.entrySet()) { if(sent++>=500) break; var created=notifications.createFromOutbox(event.eventId(),recipient.getKey(),NotificationType.NEED_MATCH_AVAILABLE,"Matching need available","A nearby request may match your offer.","NEED_REQUEST",need.getId(),Map.of("needRequestId",need.getId().toString(),"merchantLocationId",recipient.getValue().toString())); if(created.created()) signals.add(new SseSignal(recipient.getKey(),"notification.created",Map.of("notificationId",created.notificationId().toString()))); signals.add(new SseSignal(recipient.getKey(),"sync-required",Map.of("needRequestId",need.getId().toString()))); }
        return signals;
    }
}
