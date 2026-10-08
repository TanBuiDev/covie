package vn.covie.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.users.api.dto.CreateUserAddressRequest;
import vn.covie.users.api.dto.UpdateUserAddressRequest;
import vn.covie.users.model.PinSource;
import vn.covie.users.model.UserAddressEntity;
import vn.covie.users.persistence.UserAddressRepository;

class UserAddressServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-09T00:00:00Z");

    @Test
    void appliesDefaultToTheManagedCopyReturnedBySave() {
        UUID userId=UUID.randomUUID();var repository=mock(UserAddressRepository.class);
        var managed=address(UUID.randomUUID(),userId,false);
        when(repository.save(any())).thenReturn(managed);
        when(repository.findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId,"ACTIVE")).thenReturn(List.of(managed));
        var result=serviceFor(userId,repository).create(request(true));
        assertThat(result).isSameAs(managed);assertThat(managed.isDefaultAddress()).isTrue();
    }
    @Test
    void explicitFalseClearsAnExistingDefault() {
        UUID userId=UUID.randomUUID();var repository=mock(UserAddressRepository.class);var target=address(UUID.randomUUID(),userId,true);
        when(repository.findByIdAndUserIdAndStatus(target.getId(),userId,"ACTIVE")).thenReturn(Optional.of(target));
        assertThat(serviceFor(userId,repository).update(target.getId(),updateRequest(false)).isDefaultAddress()).isFalse();
    }
    @Test
    void makesTheFirstAddressDefaultWhenTheRequestDoesNot() {
        UUID userId = UUID.randomUUID();
        UserAddressRepository addresses = mock(UserAddressRepository.class);
        when(addresses.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(addresses.countByUserIdAndStatus(userId, "ACTIVE")).thenReturn(1L);
        when(addresses.findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId, "ACTIVE")).thenReturn(List.of());
        UserAddressService service = serviceFor(userId, addresses);

        UserAddressEntity created = service.create(request(false));

        assertThat(created.isDefaultAddress()).isTrue();
    }

    @Test
    void switchingDefaultLeavesExactlyOneActiveDefault() {
        UUID userId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UserAddressRepository addresses = mock(UserAddressRepository.class);
        UserAddressEntity oldDefault = address(UUID.randomUUID(), userId, true);
        UserAddressEntity target = address(targetId, userId, false);
        when(addresses.findByIdAndUserIdAndStatus(targetId, userId, "ACTIVE")).thenReturn(Optional.of(target));
        when(addresses.findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId, "ACTIVE"))
                .thenReturn(List.of(oldDefault, target));
        UserAddressService service = serviceFor(userId, addresses);

        UserAddressEntity updated = service.update(targetId, updateRequest(true));

        assertThat(List.of(oldDefault, updated).stream().filter(UserAddressEntity::isDefaultAddress)).hasSize(1);
        assertThat(updated.isDefaultAddress()).isTrue();
    }

    @Test
    void hidesAnotherUsersAddressAsNotFound() {
        UUID userId = UUID.randomUUID();
        UUID otherUsersAddressId = UUID.randomUUID();
        UserAddressRepository addresses = mock(UserAddressRepository.class);
        when(addresses.findByIdAndUserIdAndStatus(otherUsersAddressId, userId, "ACTIVE")).thenReturn(Optional.empty());
        UserAddressService service = serviceFor(userId, addresses);

        assertThatThrownBy(() -> service.get(otherUsersAddressId))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void deletingDefaultPromotesTheMostRecentlyUpdatedActiveAddress() {
        UUID userId = UUID.randomUUID();
        UUID defaultId = UUID.randomUUID();
        UserAddressRepository addresses = mock(UserAddressRepository.class);
        UserAddressEntity defaultAddress = address(defaultId, userId, true);
        UserAddressEntity replacement = address(UUID.randomUUID(), userId, false);
        when(addresses.findByIdAndUserIdAndStatus(defaultId, userId, "ACTIVE")).thenReturn(Optional.of(defaultAddress));
        when(addresses.findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId, "ACTIVE")).thenReturn(List.of(replacement));
        UserAddressService service = serviceFor(userId, addresses);

        service.delete(defaultId);

        assertThat(defaultAddress.getStatus()).isEqualTo("DELETED");
        assertThat(defaultAddress.isDefaultAddress()).isFalse();
        assertThat(defaultAddress.getDeletedAt()).isEqualTo(NOW);
        assertThat(replacement.isDefaultAddress()).isTrue();
    }

    private UserAddressService serviceFor(UUID userId, UserAddressRepository addresses) {
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(userId, Set.of(SystemRole.USER), UUID.randomUUID()));
        AppClock clock = () -> NOW;
        return new UserAddressService(actors, addresses, clock);
    }

    private CreateUserAddressRequest request(boolean defaultAddress) {
        return new CreateUserAddressRequest("Home", "Nguyen Van A", "0900000000", "1 Main Street", 10.8412, 106.8374,
                PinSource.GPS, "Ring the bell", defaultAddress);
    }

    private UpdateUserAddressRequest updateRequest(boolean defaultAddress) {
        return new UpdateUserAddressRequest("Office", "Nguyen Van B", "0911111111", "2 Work Street", 10.8413, 106.8375,
                PinSource.USER_ADJUSTED, "Call first", defaultAddress);
    }

    private UserAddressEntity address(UUID id, UUID userId, boolean defaultAddress) {
        return UserAddressEntity.create(id, userId, "Home", null, null, "1 Main Street", 10.8412, 106.8374,
                PinSource.GPS, null, defaultAddress, NOW);
    }
}
