package vn.covie.merchants.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import vn.covie.media.application.MediaAttachmentPolicy;
import vn.covie.merchants.api.dto.DayScheduleRequest;
import vn.covie.merchants.api.dto.MerchantContactRequest;
import vn.covie.merchants.api.dto.ReplaceLocationHoursRequest;
import vn.covie.merchants.api.dto.TimeIntervalRequest;
import vn.covie.merchants.model.ContactStatus;
import vn.covie.merchants.model.ContactType;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.model.OperationType;
import vn.covie.merchants.persistence.MerchantContactMethodRepository;
import vn.covie.merchants.persistence.MerchantLocationHourRepository;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.merchants.security.MerchantAccess;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.support.PostgresIntegrationTest;

class MerchantOperationsConcurrencyIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired MerchantHoursService hours;
    @Autowired MerchantContactService contactService;
    @Autowired MerchantProfileRepository profiles;
    @Autowired MerchantLocationRepository locations;
    @Autowired MerchantLocationHourRepository hourRows;
    @Autowired MerchantContactMethodRepository contacts;
    @MockitoBean MerchantAccessPolicy access;
    @MockitoBean MediaAttachmentPolicy media;

    @Test
    void concurrentWeeklyReplacementsLeaveExactlyOneCompleteSnapshot() throws Exception {
        Fixture fixture = fixture();
        stubAccess(fixture);
        ReplaceLocationHoursRequest shop = schedule(1, OperationType.SHOP);
        ReplaceLocationHoursRequest pickup = schedule(2, OperationType.PICKUP);

        runConcurrently(
                () -> hours.replace(fixture.merchantId(), fixture.locationId(), shop),
                () -> hours.replace(fixture.merchantId(), fixture.locationId(), pickup));

        var persisted = hourRows
                .findAllByMerchantLocationIdOrderByDayOfWeekAscOperationTypeAscSortOrderAsc(fixture.locationId());
        assertThat(persisted).hasSize(20);
        assertThat(persisted.stream().map(row -> row.getDayOfWeek() + ":" + row.getOperationType()).collect(
                java.util.stream.Collectors.toSet()))
                .isIn(Set.of("1:SHOP"), Set.of("2:PICKUP"));
    }

    @Test
    void concurrentPrimaryPromotionsLeaveOneActivePrimaryInMerchantScope() throws Exception {
        Fixture fixture = fixture();
        stubAccess(fixture);

        runConcurrently(
                () -> contactService.create(fixture.merchantId(), phone("0900000001", null)),
                () -> contactService.create(fixture.merchantId(), phone("0900000002", null)));

        var active = contacts.findAllByMerchantProfileIdAndStatusOrderByCreatedAtAscIdAsc(
                fixture.merchantId(), ContactStatus.ACTIVE);
        assertThat(active).hasSize(2);
        assertThat(active).filteredOn(contact -> contact.isPrimary()).hasSize(1);
    }

    @Test
    void concurrentPrimaryPromotionsLeaveOneActivePrimaryInLocationScope() throws Exception {
        Fixture fixture = fixture();
        stubAccess(fixture);

        runConcurrently(
                () -> contactService.create(fixture.merchantId(), phone("0900000003", fixture.locationId())),
                () -> contactService.create(fixture.merchantId(), phone("0900000004", fixture.locationId())));

        var active = contacts.findAllByMerchantProfileIdAndStatusOrderByCreatedAtAscIdAsc(
                fixture.merchantId(), ContactStatus.ACTIVE);
        assertThat(active).hasSize(2);
        assertThat(active).filteredOn(contact -> contact.isPrimary()).hasSize(1);
    }

    private Fixture fixture() {
        UUID merchantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        profiles.saveAndFlush(MerchantProfileEntity.create(merchantId, "Concurrent merchant",
                "concurrent-" + merchantId, NOW));
        locations.saveAndFlush(MerchantLocationEntity.create(locationId, merchantId, "Concurrent location",
                "1 CoVie Street", 10.8, 106.7, NOW));
        return new Fixture(merchantId, locationId, UUID.randomUUID(), UUID.randomUUID());
    }

    private void stubAccess(Fixture fixture) {
        when(access.require(any(UUID.class), any())).thenReturn(new MerchantAccess(
                fixture.userId(), fixture.merchantId(), fixture.membershipId(), MerchantRole.OWNER));
        when(access.requireLocation(any(UUID.class), any(UUID.class), any())).thenReturn(new MerchantAccess(
                fixture.userId(), fixture.merchantId(), fixture.membershipId(), MerchantRole.OWNER));
    }

    private static ReplaceLocationHoursRequest schedule(int day, OperationType operation) {
        List<TimeIntervalRequest> intervals = java.util.stream.IntStream.range(0, 20)
                .mapToObj(index -> new TimeIntervalRequest(LocalTime.of(0, index * 2),
                        LocalTime.of(0, index * 2 + 1))).toList();
        return new ReplaceLocationHoursRequest(List.of(new DayScheduleRequest(day, operation, false, intervals)));
    }

    private static MerchantContactRequest phone(String value, UUID locationId) {
        return new MerchantContactRequest(locationId, ContactType.PHONE, value, null, true, true);
    }

    private static void runConcurrently(ThrowingRunnable firstAction, ThrowingRunnable secondAction) throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> runAfterStart(firstAction, ready, start));
            Future<?> second = executor.submit(() -> runAfterStart(secondAction, ready, start));
            ready.await();
            start.countDown();
            first.get();
            second.get();
        }
    }

    private static void runAfterStart(ThrowingRunnable action, CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            start.await();
            action.run();
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }

    private record Fixture(UUID merchantId, UUID locationId, UUID userId, UUID membershipId) {}
    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}
