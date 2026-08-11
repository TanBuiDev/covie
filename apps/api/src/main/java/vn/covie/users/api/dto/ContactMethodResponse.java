package vn.covie.users.api.dto;

import java.time.Instant;
import java.util.UUID;
import vn.covie.users.model.ContactType;
import vn.covie.users.model.UserContactMethodEntity;

public record ContactMethodResponse(UUID id, ContactType type, String value, boolean primary, boolean verified, String status, Instant createdAt) {
    public static ContactMethodResponse from(UserContactMethodEntity contact) { return new ContactMethodResponse(contact.getId(), contact.getContactType(), contact.getValue(), contact.isPrimary(), contact.isVerified(), contact.getStatus(), contact.getCreatedAt()); }
}
