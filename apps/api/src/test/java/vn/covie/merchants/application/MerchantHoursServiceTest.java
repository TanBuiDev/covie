package vn.covie.merchants.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.merchants.api.dto.DayScheduleRequest;
import vn.covie.merchants.api.dto.ReplaceLocationHoursRequest;
import vn.covie.merchants.api.dto.TimeIntervalRequest;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantLocationHourEntity;
import vn.covie.merchants.model.MerchantLocationHourExceptionEntity;
import vn.covie.merchants.model.OperationType;
import vn.covie.merchants.persistence.MerchantLocationHourExceptionRepository;
import vn.covie.merchants.persistence.MerchantLocationHourRepository;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class MerchantHoursServiceTest {
    @Test
    void rejectsOverlappingIntervalsBeforeReplacingPersistedHours() {
        Fixture fixture = fixture();
        ReplaceLocationHoursRequest request = new ReplaceLocationHoursRequest(List.of(new DayScheduleRequest(
                1, OperationType.SHOP, false, List.of(
                        new TimeIntervalRequest(LocalTime.of(8, 0), LocalTime.of(12, 0)),
                        new TimeIntervalRequest(LocalTime.of(11, 30), LocalTime.of(14, 0))))));

        assertThatThrownBy(() -> fixture.service().replace(fixture.merchantId(), fixture.locationId(), request))
                .isInstanceOf(ApiException.class)
                .satisfies(error -> assertThat(((ApiException) error).code()).isEqualTo(ErrorCode.VALIDATION_FAILED));
    }

    @Test
    void explicitClosedExceptionOverridesAnOpenWeeklyWindow() {
        Fixture fixture = fixture();
        Instant mondayTenAmVietnam = Instant.parse("2026-01-05T03:00:00Z");
        when(fixture.exceptions().findAllByMerchantLocationIdAndLocalDateAndOperationTypeOrderBySortOrderAsc(
                fixture.locationId(), LocalDate.of(2026, 1, 5), OperationType.SHOP))
                .thenReturn(List.of(MerchantLocationHourExceptionEntity.closed(UUID.randomUUID(), fixture.locationId(),
                        LocalDate.of(2026, 1, 5), OperationType.SHOP, "Holiday")));
        when(fixture.hours().findAllByMerchantLocationIdAndDayOfWeekAndOperationTypeOrderBySortOrderAsc(
                fixture.locationId(), (short) 1, OperationType.SHOP))
                .thenReturn(List.of(MerchantLocationHourEntity.open(UUID.randomUUID(), fixture.locationId(), 1,
                        OperationType.SHOP, LocalTime.of(8, 0), LocalTime.of(18, 0), 0)));

        assertThat(fixture.service().isOpen(fixture.locationId(), OperationType.SHOP, mondayTenAmVietnam)).isFalse();
    }

    @Test
    void usesLongitudeFirstLatitudeSecondGeometryWithoutChangingVietnamLocalTimeEvaluation() {
        Fixture fixture = fixture();
        Instant mondayTenAmVietnam = Instant.parse("2026-01-05T03:00:00Z");
        when(fixture.exceptions().findAllByMerchantLocationIdAndLocalDateAndOperationTypeOrderBySortOrderAsc(
                fixture.locationId(), LocalDate.of(2026, 1, 5), OperationType.PICKUP)).thenReturn(List.of());
        when(fixture.hours().findAllByMerchantLocationIdAndDayOfWeekAndOperationTypeOrderBySortOrderAsc(
                fixture.locationId(), (short) 1, OperationType.PICKUP))
                .thenReturn(List.of(MerchantLocationHourEntity.open(UUID.randomUUID(), fixture.locationId(), 1,
                        OperationType.PICKUP, LocalTime.of(9, 0), LocalTime.of(11, 0), 0)));

        assertThat(fixture.service().isOpen(fixture.locationId(), OperationType.PICKUP, mondayTenAmVietnam)).isTrue();
        assertThat(fixture.location().getGeoPoint().getX()).isEqualTo(106.7);
        assertThat(fixture.location().getGeoPoint().getY()).isEqualTo(10.8);
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        UUID merchantId = UUID.randomUUID();
        UUID locationId = UUID.randomUUID();
        MerchantLocationEntity location = MerchantLocationEntity.create(locationId, merchantId, "Shop", "Address",
                10.8, 106.7, Instant.parse("2026-01-01T00:00:00Z"));
        MerchantAccessPolicy access = mock(MerchantAccessPolicy.class);
        MerchantLocationRepository locations = mock(MerchantLocationRepository.class);
        MerchantLocationHourRepository hours = mock(MerchantLocationHourRepository.class);
        MerchantLocationHourExceptionRepository exceptions = mock(MerchantLocationHourExceptionRepository.class);
        when(locations.findById(locationId)).thenReturn(java.util.Optional.of(location));
        when(locations.findByIdAndMerchantProfileIdAndDeletedAtIsNull(locationId, merchantId))
                .thenReturn(java.util.Optional.of(location));
        when(hours.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        return new Fixture(merchantId, locationId, location, hours, exceptions,
                new MerchantHoursService(access, locations, hours, exceptions, new UuidV7Generator()));
    }

    private record Fixture(UUID merchantId, UUID locationId, MerchantLocationEntity location,
            MerchantLocationHourRepository hours, MerchantLocationHourExceptionRepository exceptions,
            MerchantHoursService service) {}
}
