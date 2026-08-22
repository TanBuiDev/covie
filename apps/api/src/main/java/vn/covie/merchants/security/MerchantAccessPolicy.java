package vn.covie.merchants.security;

import java.util.UUID;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantMemberLocationEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMemberLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public final class MerchantAccessPolicy {
    private final CurrentActorProvider actors;
    private final MerchantMembershipRepository memberships;
    private final MerchantMemberLocationRepository memberLocations;
    private final MerchantLocationRepository locations;

    public MerchantAccessPolicy(CurrentActorProvider actors, MerchantMembershipRepository memberships,
            MerchantMemberLocationRepository memberLocations, MerchantLocationRepository locations) {
        this.actors = actors;
        this.memberships = memberships;
        this.memberLocations = memberLocations;
        this.locations = locations;
    }

    public MerchantAccess require(UUID merchantId, MerchantCapability capability) {
        CurrentActor actor = actors.currentActor();
        MerchantMembershipEntity membership = memberships.findActive(merchantId, actor.userId())
                .orElseThrow(MerchantAccessPolicy::notFound);
        if (membership.role() == MerchantRole.STAFF
                && !memberLocations.existsActiveLocationAssignment(membership.getId())) {
            throw notFound();
        }
        if (!allows(membership.role(), capability)) throw notAllowed();
        return new MerchantAccess(actor.userId(), merchantId, membership.getId(), membership.role());
    }

    public MerchantAccess requireLocation(UUID merchantId, UUID locationId, MerchantCapability capability) {
        CurrentActor actor = actors.currentActor();
        MerchantMembershipEntity membership = memberships.findActive(merchantId, actor.userId())
                .orElseThrow(MerchantAccessPolicy::notFound);
        MerchantLocationEntity location = locations.findByIdAndMerchantProfileIdAndDeletedAtIsNull(locationId, merchantId)
                .orElseThrow(MerchantAccessPolicy::notFound);
        if (membership.role() == MerchantRole.STAFF
                && !memberLocations.existsByMembershipIdAndMerchantLocationId(membership.getId(), location.getId())) {
            throw notFound();
        }
        if (!allows(membership.role(), capability)) throw notAllowed();
        return new MerchantAccess(actor.userId(), merchantId, membership.getId(), membership.role());
    }

    /** Empty means the member can read every location of this merchant. */
    public Set<UUID> readableOrderLocations(UUID merchantId) {
        MerchantAccess access = require(merchantId, MerchantCapability.VIEW_ORDERS);
        if (access.role() != MerchantRole.STAFF) return Set.of();
        return memberLocations.findAllByMembershipId(access.membershipId()).stream()
                .map(MerchantMemberLocationEntity::getMerchantLocationId).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    static boolean allows(MerchantRole role, MerchantCapability capability) {
        return switch (role) {
            case OWNER -> true;
            case MANAGER -> capability != MerchantCapability.GRANT_OWNER;
            case STAFF -> switch (capability) {
                case VIEW_MERCHANT, MANAGE_LOCATION_OFFER, UPLOAD_MERCHANT_MEDIA -> true;
                case VIEW_ORDERS, PROCESS_ORDERS -> true;
                case EDIT_PROFILE, MANAGE_MEMBERS, GRANT_OWNER, MANAGE_LOCATIONS, MANAGE_HOURS_CONTACTS,
                        MANAGE_CATALOG_STRUCTURE, PUBLISH_CATALOG -> false;
            };
        };
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }

    private static ApiException notAllowed() {
        return new ApiException(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED, HttpStatus.FORBIDDEN,
                "Merchant action is not allowed.");
    }
}
