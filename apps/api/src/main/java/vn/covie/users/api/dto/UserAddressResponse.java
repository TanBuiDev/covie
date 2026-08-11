package vn.covie.users.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.users.model.PinSource;
import vn.covie.users.model.UserAddressEntity;

public record UserAddressResponse(
        UUID id,
        String label,
        String recipientName,
        String phone,
        String addressText,
        Double latitude,
        Double longitude,
        PinSource pinSource,
        String note,
        boolean defaultAddress,
        long version,
        Instant createdAt,
        Instant updatedAt) {
    public static UserAddressResponse from(UserAddressEntity address) {
        return new UserAddressResponse(address.getId(), address.getLabel(), address.getRecipientName(), address.getPhone(),
                address.getAddressText(), address.getGeoPoint().getY(), address.getGeoPoint().getX(), address.getPinSource(),
                address.getNote(), address.isDefaultAddress(), address.getVersion(), address.getCreatedAt(), address.getUpdatedAt());
    }
}
