package vn.covie.merchants.application;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.media.application.MediaAttachmentPolicy;
import vn.covie.merchants.api.dto.MerchantContactRequest;
import vn.covie.merchants.api.dto.MerchantContactResponse;
import vn.covie.merchants.model.ContactStatus;
import vn.covie.merchants.model.ContactType;
import vn.covie.merchants.model.MerchantContactMethodEntity;
import vn.covie.merchants.persistence.MerchantContactMethodRepository;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.merchants.security.MerchantAccess;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class MerchantContactService {
    private final MerchantAccessPolicy access;
    private final MerchantContactMethodRepository contacts;
    private final MerchantLocationRepository locations;
    private final MerchantProfileRepository profiles;
    private final MediaAttachmentPolicy media;
    private final UuidV7Generator ids;
    private final AppClock clock;

    public MerchantContactService(MerchantAccessPolicy access, MerchantContactMethodRepository contacts,
            MerchantLocationRepository locations, MerchantProfileRepository profiles, MediaAttachmentPolicy media,
            UuidV7Generator ids, AppClock clock) {
        this.access = access; this.contacts = contacts; this.locations = locations; this.profiles = profiles;
        this.media = media;
        this.ids = ids; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<MerchantContactResponse> list(UUID merchantId) {
        access.require(merchantId, MerchantCapability.MANAGE_HOURS_CONTACTS);
        return contacts.findAllByMerchantProfileIdAndStatusOrderByCreatedAtAscIdAsc(merchantId, ContactStatus.ACTIVE)
                .stream().map(MerchantContactService::response).toList();
    }

    @Transactional
    public MerchantContactResponse create(UUID merchantId, MerchantContactRequest request) {
        MerchantAccess granted = access.require(merchantId, MerchantCapability.MANAGE_HOURS_CONTACTS);
        lockMerchant(merchantId);
        lockLocations(merchantId, request.merchantLocationId(), request.merchantLocationId());
        ContactPayload payload = validate(merchantId, request, granted.userId());
        if (request.primary()) demotePrimary(merchantId, request.merchantLocationId(), request.contactType());
        MerchantContactMethodEntity contact = MerchantContactMethodEntity.create(ids.generate(), merchantId,
                request.merchantLocationId(), request.contactType(), payload.value(), payload.qrMediaId(),
                request.primary(), request.publicContact(), clock.now());
        return response(contacts.saveAndFlush(contact));
    }

    @Transactional
    public MerchantContactResponse update(UUID merchantId, UUID contactId, String ifMatch,
            MerchantContactRequest request) {
        MerchantAccess granted = access.require(merchantId, MerchantCapability.MANAGE_HOURS_CONTACTS);
        lockMerchant(merchantId);
        MerchantContactMethodEntity contact = requireContactForUpdate(merchantId, contactId);
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), contact.getVersion());
        lockLocations(merchantId, contact.getMerchantLocationId(), request.merchantLocationId());
        ContactPayload payload = validate(merchantId, request, granted.userId());
        if (request.primary()) demotePrimary(merchantId, request.merchantLocationId(), request.contactType());
        contact.update(request.merchantLocationId(), request.contactType(), payload.value(), payload.qrMediaId(),
                request.primary(), request.publicContact());
        return response(contacts.saveAndFlush(contact));
    }

    @Transactional
    public void delete(UUID merchantId, UUID contactId) {
        access.require(merchantId, MerchantCapability.MANAGE_HOURS_CONTACTS);
        lockMerchant(merchantId);
        MerchantContactMethodEntity contact = requireContactForUpdate(merchantId, contactId);
        lockLocations(merchantId, contact.getMerchantLocationId(), contact.getMerchantLocationId());
        contact.deactivate();
        contacts.save(contact);
    }

    private ContactPayload validate(UUID merchantId, MerchantContactRequest request, UUID actorUserId) {
        if (request == null || request.contactType() == null) throw validation("Contact type is required.");
        if (request.contactType() == ContactType.ZALO_QR) {
            if (request.qrMediaId() == null) throw validation("ZALO_QR requires qrMediaId.");
            if (request.value() != null && !request.value().isBlank()) {
                throw validation("ZALO_QR does not accept a text value.");
            }
            media.requireMerchantContactQr(request.qrMediaId(), actorUserId);
            return new ContactPayload(null, request.qrMediaId());
        }
        String value = request.value() == null ? "" : request.value().strip();
        if (value.isBlank()) throw validation("Contact value must not be blank.");
        if (request.qrMediaId() != null) throw validation("Only ZALO_QR accepts qrMediaId.");
        return new ContactPayload(value, null);
    }

    private void demotePrimary(UUID merchantId, UUID locationId, ContactType type) {
        contacts.findAllByMerchantProfileIdAndMerchantLocationIdAndContactTypeAndStatus(
                merchantId, locationId, type, ContactStatus.ACTIVE).forEach(MerchantContactMethodEntity::demote);
        contacts.flush();
    }

    private MerchantContactMethodEntity requireContactForUpdate(UUID merchantId, UUID contactId) {
        return contacts.findActiveForUpdate(contactId, merchantId)
                .orElseThrow(MerchantContactService::notFound);
    }

    private void lockMerchant(UUID merchantId) {
        profiles.findActiveForUpdate(merchantId).orElseThrow(MerchantContactService::notFound);
    }

    private void lockLocations(UUID merchantId, UUID oldLocationId, UUID newLocationId) {
        java.util.stream.Stream.of(oldLocationId, newLocationId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(locationId -> locations.findActiveForUpdate(locationId, merchantId)
                        .orElseThrow(MerchantContactService::notFound));
    }

    private static MerchantContactResponse response(MerchantContactMethodEntity contact) {
        return new MerchantContactResponse(contact.getId(), contact.getMerchantProfileId(),
                contact.getMerchantLocationId(), contact.getContactType(), contact.getValue(), contact.getQrMediaId(),
                contact.isPrimary(), contact.isPublicContact(), contact.getStatus(), contact.getVersion());
    }

    private record ContactPayload(String value, UUID qrMediaId) {}
    private static ApiException validation(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, message);
    }
    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
