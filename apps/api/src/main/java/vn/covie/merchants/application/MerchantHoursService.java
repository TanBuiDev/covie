package vn.covie.merchants.application;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.merchants.api.dto.DayScheduleRequest;
import vn.covie.merchants.api.dto.MerchantHoursResponse;
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
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class MerchantHoursService {
    private final MerchantAccessPolicy access;
    private final MerchantLocationRepository locations;
    private final MerchantLocationHourRepository hours;
    private final MerchantLocationHourExceptionRepository exceptions;
    private final UuidV7Generator ids;

    public MerchantHoursService(MerchantAccessPolicy access, MerchantLocationRepository locations,
            MerchantLocationHourRepository hours, MerchantLocationHourExceptionRepository exceptions,
            UuidV7Generator ids) {
        this.access = access; this.locations = locations; this.hours = hours; this.exceptions = exceptions;
        this.ids = ids;
    }

    @Transactional(readOnly = true)
    public MerchantHoursResponse get(UUID merchantId, UUID locationId) {
        access.requireLocation(merchantId, locationId, MerchantCapability.VIEW_MERCHANT);
        requireLocation(merchantId, locationId);
        return response(locationId, hours.findAllByMerchantLocationIdOrderByDayOfWeekAscOperationTypeAscSortOrderAsc(
                locationId));
    }

    @Transactional
    public MerchantHoursResponse replace(UUID merchantId, UUID locationId, ReplaceLocationHoursRequest request) {
        access.requireLocation(merchantId, locationId, MerchantCapability.MANAGE_HOURS_CONTACTS);
        requireLocationForUpdate(merchantId, locationId);
        List<MerchantLocationHourEntity> replacement = validateAndMap(locationId, request);
        hours.deleteAllByMerchantLocationId(locationId);
        hours.flush();
        return response(locationId, hours.saveAllAndFlush(replacement));
    }

    @Transactional(readOnly = true)
    public boolean isOpen(UUID locationId, OperationType operation, Instant instant) {
        if (operation == null || instant == null) throw validation("Operation and instant are required.");
        MerchantLocationEntity location = locations.findById(locationId)
                .filter(candidate -> candidate.getDeletedAt() == null)
                .orElseThrow(MerchantHoursService::notFound);
        ZonedDateTime local = instant.atZone(ZoneId.of(location.getTimezone()));
        List<MerchantLocationHourExceptionEntity> overrides =
                exceptions.findAllByMerchantLocationIdAndLocalDateAndOperationTypeOrderBySortOrderAsc(
                        locationId, local.toLocalDate(), operation);
        if (!overrides.isEmpty()) return matchesExceptions(overrides, local.toLocalTime());
        return matchesHours(hours.findAllByMerchantLocationIdAndDayOfWeekAndOperationTypeOrderBySortOrderAsc(
                locationId, (short) local.getDayOfWeek().getValue(), operation), local.toLocalTime());
    }

    private List<MerchantLocationHourEntity> validateAndMap(UUID locationId, ReplaceLocationHoursRequest request) {
        if (request == null || request.days() == null) throw validation("Schedule days are required.");
        Set<String> keys = new HashSet<>();
        List<MerchantLocationHourEntity> result = new ArrayList<>();
        for (DayScheduleRequest day : request.days()) {
            if (day == null || day.operationType() == null || day.dayOfWeek() < 1 || day.dayOfWeek() > 7) {
                throw validation("Day and operation are invalid.");
            }
            if (!keys.add(day.dayOfWeek() + ":" + day.operationType())) {
                throw validation("A day and operation schedule may appear only once.");
            }
            List<TimeIntervalRequest> intervals = day.intervals() == null ? List.of() : day.intervals();
            if (day.closed()) {
                if (!intervals.isEmpty()) throw validation("Closed schedules cannot contain intervals.");
                result.add(MerchantLocationHourEntity.closed(ids.generate(), locationId, day.dayOfWeek(),
                        day.operationType()));
                continue;
            }
            if (intervals.isEmpty()) throw validation("Open schedules require at least one interval.");
            if (intervals.stream().anyMatch(java.util.Objects::isNull)) {
                throw validation("Hour intervals must not be null.");
            }
            List<TimeIntervalRequest> sorted = intervals.stream()
                    .sorted(Comparator.comparing(TimeIntervalRequest::openTime,
                            Comparator.nullsFirst(Comparator.naturalOrder())))
                    .toList();
            LocalTime previousClose = null;
            for (int index = 0; index < sorted.size(); index++) {
                TimeIntervalRequest interval = sorted.get(index);
                if (interval == null || interval.openTime() == null || interval.closeTime() == null
                        || !interval.openTime().isBefore(interval.closeTime())) {
                    throw validation("Hour intervals require open time before close time.");
                }
                if (previousClose != null && interval.openTime().isBefore(previousClose)) {
                    throw validation("Hour intervals cannot overlap.");
                }
                result.add(MerchantLocationHourEntity.open(ids.generate(), locationId, day.dayOfWeek(),
                        day.operationType(), interval.openTime(), interval.closeTime(), index));
                previousClose = interval.closeTime();
            }
        }
        return result;
    }

    private MerchantLocationEntity requireLocation(UUID merchantId, UUID locationId) {
        return locations.findByIdAndMerchantProfileIdAndDeletedAtIsNull(locationId, merchantId)
                .orElseThrow(MerchantHoursService::notFound);
    }

    private MerchantLocationEntity requireLocationForUpdate(UUID merchantId, UUID locationId) {
        return locations.findActiveForUpdate(locationId, merchantId).orElseThrow(MerchantHoursService::notFound);
    }

    private static boolean matchesHours(List<MerchantLocationHourEntity> windows, LocalTime time) {
        return windows.stream().anyMatch(window -> !window.isClosed() && contains(window.getOpenTime(),
                window.getCloseTime(), time));
    }

    private static boolean matchesExceptions(List<MerchantLocationHourExceptionEntity> windows, LocalTime time) {
        return windows.stream().anyMatch(window -> !window.isClosed() && contains(window.getOpenTime(),
                window.getCloseTime(), time));
    }

    private static boolean contains(LocalTime open, LocalTime close, LocalTime time) {
        return !time.isBefore(open) && time.isBefore(close);
    }

    private static MerchantHoursResponse response(UUID locationId, List<MerchantLocationHourEntity> entities) {
        record Key(short day, OperationType operation) {}
        java.util.LinkedHashMap<Key, List<MerchantLocationHourEntity>> groups = new java.util.LinkedHashMap<>();
        entities.stream().sorted(Comparator.comparingInt(MerchantLocationHourEntity::getDayOfWeek)
                .thenComparing(MerchantLocationHourEntity::getOperationType)
                .thenComparingInt(MerchantLocationHourEntity::getSortOrder))
                .forEach(entity -> groups.computeIfAbsent(new Key(entity.getDayOfWeek(), entity.getOperationType()),
                        ignored -> new ArrayList<>()).add(entity));
        List<DayScheduleRequest> days = groups.entrySet().stream().map(entry -> {
            boolean closed = entry.getValue().stream().anyMatch(MerchantLocationHourEntity::isClosed);
            List<TimeIntervalRequest> intervals = entry.getValue().stream().filter(hour -> !hour.isClosed())
                    .map(hour -> new TimeIntervalRequest(hour.getOpenTime(), hour.getCloseTime())).toList();
            return new DayScheduleRequest(entry.getKey().day(), entry.getKey().operation(), closed, intervals);
        }).toList();
        return new MerchantHoursResponse(locationId, days);
    }

    private static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
