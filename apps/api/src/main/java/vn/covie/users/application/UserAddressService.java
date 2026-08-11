package vn.covie.users.application;

import java.util.UUID;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.shared.time.AppClock;
import vn.covie.users.api.dto.CreateUserAddressRequest;
import vn.covie.users.api.dto.UpdateUserAddressRequest;
import vn.covie.users.model.UserAddressEntity;
import vn.covie.users.persistence.UserAddressRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import org.springframework.http.HttpStatus;

@Service
public class UserAddressService {
    private static final String ACTIVE = "ACTIVE";
    private final CurrentActorProvider actors; private final UserAddressRepository addresses; private final AppClock clock;

    public UserAddressService(CurrentActorProvider actors, UserAddressRepository addresses, AppClock clock) {
        this.actors = actors; this.addresses = addresses; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserAddressEntity> active() { return addresses.findAllByUserIdAndStatusOrderByUpdatedAtDesc(actors.currentActor().userId(), ACTIVE); }

    @Transactional(readOnly = true)
    public UserAddressEntity get(UUID id) { return owned(id, actors.currentActor().userId()); }

    @Transactional
    public UserAddressEntity create(CreateUserAddressRequest request) {
        UUID userId = actors.currentActor().userId();
        UserAddressEntity address = UserAddressEntity.create(UUID.randomUUID(), userId, request.label().trim(),
                request.recipientName(), request.phone(), request.addressText().trim(), request.latitude(), request.longitude(),
                request.pinSource(), request.note(), false, clock.now());
        addresses.save(address);
        if (request.defaultAddress() || addresses.countByUserIdAndStatus(userId, ACTIVE) == 1) makeDefault(userId, address);
        return address;
    }

    @Transactional
    public UserAddressEntity update(UUID id, UpdateUserAddressRequest request) {
        UUID userId = actors.currentActor().userId();
        UserAddressEntity address = owned(id, userId);
        address.update(request.label().trim(), request.recipientName(), request.phone(), request.addressText().trim(),
                request.latitude(), request.longitude(), request.pinSource(), request.note(), clock.now());
        if (request.defaultAddress()) makeDefault(userId, address);
        return address;
    }

    private void makeDefault(UUID userId, UserAddressEntity target) {
        addresses.findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId, ACTIVE).forEach(address -> address.setDefaultAddress(false));
        addresses.flush();
        target.setDefaultAddress(true);
    }

    @Transactional
    public void delete(UUID id) {
        UUID userId = actors.currentActor().userId();
        UserAddressEntity address = owned(id, userId);
        boolean wasDefault = address.isDefaultAddress();
        address.delete(clock.now());
        if (wasDefault) addresses.findAllByUserIdAndStatusOrderByUpdatedAtDesc(userId, ACTIVE).stream()
                .findFirst().ifPresent(replacement -> replacement.setDefaultAddress(true));
    }

    private UserAddressEntity owned(UUID id, UUID userId) {
        return addresses.findByIdAndUserIdAndStatus(id, userId, ACTIVE).orElseThrow(
                () -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Address not found"));
    }
}
