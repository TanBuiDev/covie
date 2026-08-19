package vn.covie.location.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.model.PinSource;
import vn.covie.users.model.UserAddressEntity;
import vn.covie.users.persistence.UserAddressRepository;

class LocationContextResolverTest {
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Test
    void rejectsLatitudeWithoutLongitudeInsteadOfGuessingTheLocation() {
        LocationContextResolver resolver = resolver(mock(CurrentActorProvider.class), mock(UserAddressRepository.class));

        assertError(() -> resolver.resolveForDiscovery(10.8, null, null),
                ErrorCode.LOCATION_CONTEXT_INVALID, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rejectsLongitudeWithoutLatitudeInsteadOfGuessingTheLocation() {
        LocationContextResolver resolver = resolver(mock(CurrentActorProvider.class), mock(UserAddressRepository.class));

        assertError(() -> resolver.resolveForDiscovery(null, 106.8, null),
                ErrorCode.LOCATION_CONTEXT_INVALID, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rejectsCoordinatesAndAddressTogetherInsteadOfSilentlyPreferringOne() {
        LocationContextResolver resolver = resolver(mock(CurrentActorProvider.class), mock(UserAddressRepository.class));

        assertError(() -> resolver.resolveForDiscovery(10.8, 106.8, UUID.randomUUID()),
                ErrorCode.LOCATION_CONTEXT_AMBIGUOUS, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void resolvesGuestCoordinatesWithLongitudeFirstGeometryAndVietnamTimezone() {
        LocationContextResolver resolver = resolver(mock(CurrentActorProvider.class), mock(UserAddressRepository.class));

        LocationContext context = resolver.resolveForDiscovery(10.8412, 106.8374, null);

        assertThat(context.source()).isEqualTo(LocationResolutionSource.COORDINATES);
        assertThat(context.point().getX()).isEqualTo(106.8374);
        assertThat(context.point().getY()).isEqualTo(10.8412);
        assertThat(context.point().getSRID()).isEqualTo(4326);
        assertThat(context.locationAreaId()).isNull();
        assertThat(context.addressId()).isNull();
        assertThat(context.timezone().getId()).isEqualTo("Asia/Ho_Chi_Minh");
    }

    @Test
    void requiresAuthenticationBeforeResolvingASavedAddress() {
        UUID addressId = UUID.randomUUID();
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenThrow(new ApiException(ErrorCode.AUTHENTICATION_REQUIRED,
                HttpStatus.UNAUTHORIZED, "Authentication required."));
        LocationContextResolver resolver = resolver(actors, mock(UserAddressRepository.class));

        assertError(() -> resolver.resolveForDiscovery(null, null, addressId),
                ErrorCode.AUTHENTICATION_REQUIRED, HttpStatus.UNAUTHORIZED);
    }

    @Test
    void hidesAnotherUsersAddressAsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UserAddressRepository addresses = mock(UserAddressRepository.class);
        when(addresses.findByIdAndUserIdAndDeletedAtIsNull(addressId, userId)).thenReturn(Optional.empty());
        LocationContextResolver resolver = resolver(actorProvider(userId), addresses);

        assertError(() -> resolver.resolveForDiscovery(null, null, addressId),
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    void resolvesAnOwnedAddressFromItsPersistedGeoPoint() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID areaId = UUID.randomUUID();
        UserAddressEntity address = UserAddressEntity.create(addressId, userId, "Home", null, null, "1 Main Street",
                10.8412, 106.8374, PinSource.GPS, null, true, NOW);
        address.setLocationAreaId(areaId);
        UserAddressRepository addresses = mock(UserAddressRepository.class);
        when(addresses.findByIdAndUserIdAndDeletedAtIsNull(addressId, userId)).thenReturn(Optional.of(address));
        LocationContextResolver resolver = resolver(actorProvider(userId), addresses);

        LocationContext context = resolver.resolveForDiscovery(null, null, addressId);

        assertThat(context.source()).isEqualTo(LocationResolutionSource.SAVED_ADDRESS);
        assertThat(context.addressId()).isEqualTo(addressId);
        assertThat(context.locationAreaId()).isEqualTo(areaId);
        assertThat(context.point()).isSameAs(address.getGeoPoint());
        assertThat(context.point().getX()).isEqualTo(106.8374);
        assertThat(context.point().getY()).isEqualTo(10.8412);
    }

    @Test
    void rejectsCoordinatesOutsideWgs84Bounds() {
        LocationContextResolver resolver = resolver(mock(CurrentActorProvider.class), mock(UserAddressRepository.class));

        assertError(() -> resolver.resolveForDiscovery(90.0001, 106.8, null),
                ErrorCode.LOCATION_CONTEXT_INVALID, HttpStatus.UNPROCESSABLE_ENTITY);
        assertError(() -> resolver.resolveForDiscovery(10.8, -180.0001, null),
                ErrorCode.LOCATION_CONTEXT_INVALID, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void rejectsNotANumberCoordinatesBeforeCreatingAGeometry() {
        LocationContextResolver resolver = resolver(mock(CurrentActorProvider.class), mock(UserAddressRepository.class));

        assertError(() -> resolver.resolveForDiscovery(Double.NaN, 106.8, null),
                ErrorCode.LOCATION_CONTEXT_INVALID, HttpStatus.UNPROCESSABLE_ENTITY);
        assertError(() -> resolver.resolveForDiscovery(10.8, Double.NaN, null),
                ErrorCode.LOCATION_CONTEXT_INVALID, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    private LocationContextResolver resolver(CurrentActorProvider actors, UserAddressRepository addresses) {
        return new LocationContextResolver(actors, addresses, new GeoPointFactory());
    }

    private CurrentActorProvider actorProvider(UUID userId) {
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(userId, Set.of(SystemRole.USER), UUID.randomUUID()));
        return actors;
    }

    private void assertError(ThrowingCallable call, ErrorCode code, HttpStatus status) {
        ApiException error = catchThrowableOfType(call, ApiException.class);
        assertThat(error.code()).isEqualTo(code);
        assertThat(error.status()).isEqualTo(status);
    }
}
