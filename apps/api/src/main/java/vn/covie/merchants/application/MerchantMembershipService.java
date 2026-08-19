package vn.covie.merchants.application;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.merchants.api.dto.AddMerchantMemberRequest;
import vn.covie.merchants.api.dto.MerchantMemberResponse;
import vn.covie.merchants.api.dto.ReplaceMemberLocationsRequest;
import vn.covie.merchants.api.dto.UpdateMerchantMemberRequest;
import vn.covie.merchants.model.MembershipStatus;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantMemberLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.security.MerchantAccess;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.security.EmailNormalizer;

@Service
public class MerchantMembershipService {
    private final CurrentActorProvider actors;
    private final MerchantAccessPolicy access;
    private final MerchantMembershipRepository memberships;
    private final MerchantMemberLocationRepository locations;
    private final MerchantLocationRepository merchantLocations;
    private final UserIdentityRepository identities;
    private final EmailNormalizer emails;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;

    public MerchantMembershipService(CurrentActorProvider actors, MerchantAccessPolicy access,
            MerchantMembershipRepository memberships, MerchantMemberLocationRepository locations,
            MerchantLocationRepository merchantLocations, UserIdentityRepository identities,
            EmailNormalizer emails, UuidV7Generator ids, AppClock clock,
            AuditLogWriter audit, RequestIdAccessor requestIds) {
        this.actors = actors; this.access = access; this.memberships = memberships; this.locations = locations;
        this.merchantLocations = merchantLocations;
        this.identities = identities; this.emails = emails; this.ids = ids; this.clock = clock;
        this.audit = audit; this.requestIds = requestIds;
    }

    @Transactional(readOnly = true)
    public List<MerchantMemberResponse> active(UUID merchantId) {
        access.require(merchantId, MerchantCapability.MANAGE_MEMBERS);
        return memberships.findActiveMemberResponses(merchantId);
    }

    @Transactional
    public MerchantMemberResponse add(UUID merchantId, AddMerchantMemberRequest request) {
        MerchantAccess actorAccess = requireForRole(merchantId, request.role());
        UserIdentityEntity identity = identities.findLocalEmail(emails.normalize(request.email())).orElseThrow(() -> new ApiException(
                ErrorCode.MERCHANT_MEMBER_USER_NOT_FOUND, HttpStatus.NOT_FOUND, "Registered email user not found."));
        if (memberships.findActiveOrInvited(merchantId, identity.getUserId()).isPresent()) throw new ApiException(
                ErrorCode.MERCHANT_MEMBER_ALREADY_EXISTS, HttpStatus.CONFLICT, "Merchant member already exists.");
        MerchantMembershipEntity member = memberships.save(MerchantMembershipEntity.active(
                ids.generate(), merchantId, identity.getUserId(), request.role(), clock.now()));
        audit.securityEvent(actorAccess.userId(), "MERCHANT_MEMBERSHIP_ADDED", "MERCHANT_MEMBERSHIP", member.getId(),
                requestIds.current(), Map.of("role", request.role().name()));
        return response(member, identity.getEmailNormalized());
    }

    @Transactional
    public MerchantMemberResponse update(UUID merchantId, UUID memberId, String ifMatch, UpdateMerchantMemberRequest request) {
        MerchantAccess actorAccess = access.require(merchantId, MerchantCapability.MANAGE_MEMBERS);
        MerchantMembershipEntity member = activeMember(merchantId, memberId);
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), member.getVersion());
        requireOwnerForOwnerRole(actorAccess, member.getRole(), request.role());
        if (member.getRole() == MerchantRole.OWNER && request.role() != MerchantRole.OWNER) requireAnotherOwner(merchantId);
        if (member.getRole() != request.role()) locations.deleteAllByMembershipId(member.getId());
        member.changeRole(request.role());
        MerchantMembershipEntity saved = memberships.saveAndFlush(member);
        audit.securityEvent(actorAccess.userId(), "MERCHANT_MEMBERSHIP_ROLE_CHANGED", "MERCHANT_MEMBERSHIP", memberId,
                requestIds.current(), Map.of("role", request.role().name()));
        return response(saved);
    }

    @Transactional
    public void revoke(UUID merchantId, UUID memberId) {
        MerchantAccess actorAccess = access.require(merchantId, MerchantCapability.MANAGE_MEMBERS);
        MerchantMembershipEntity member = activeMember(merchantId, memberId);
        requireOwnerForOwnerRole(actorAccess, member.getRole(), member.getRole());
        if (member.getRole() == MerchantRole.OWNER) requireAnotherOwner(merchantId);
        locations.deleteAllByMembershipId(member.getId());
        member.revoke();
        memberships.save(member);
        audit.securityEvent(actorAccess.userId(), "MERCHANT_MEMBERSHIP_REVOKED", "MERCHANT_MEMBERSHIP", memberId,
                requestIds.current(), Map.of());
    }

    @Transactional
    public long replaceLocations(UUID merchantId, UUID memberId, String ifMatch,
            ReplaceMemberLocationsRequest request) {
        MerchantAccess actorAccess = access.require(merchantId, MerchantCapability.MANAGE_MEMBERS);
        MerchantMembershipEntity member = activeMember(merchantId, memberId);
        long currentVersion = member.getVersion();
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), currentVersion);
        if (member.getRole() != MerchantRole.STAFF) {
            throw new ApiException(ErrorCode.MEMBERSHIP_ROLE_NOT_LOCATION_SCOPED,
                    HttpStatus.UNPROCESSABLE_ENTITY, "Membership role is not location scoped.");
        }
        List<UUID> requested = request.locationIds();
        if (new HashSet<>(requested).size() != requested.size()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Duplicate location IDs are not allowed.");
        }
        requested.forEach(locationId -> merchantLocations
                .findByIdAndMerchantProfileIdAndDeletedAtIsNull(locationId, merchantId)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND,
                        "Resource not found.")));
        locations.deleteAllByMembershipId(memberId);
        locations.flush();
        locations.saveAll(requested.stream()
                .map(locationId -> member.assignTo(ids, locationId, clock.now())).toList());
        locations.flush();
        if (memberships.incrementVersion(memberId, currentVersion) != 1) {
            throw new ApiException(ErrorCode.RESOURCE_VERSION_CONFLICT, HttpStatus.CONFLICT,
                    "Resource version has changed.");
        }
        audit.securityEvent(actorAccess.userId(), "MERCHANT_MEMBER_LOCATIONS_REPLACED", "MERCHANT_MEMBERSHIP",
                memberId, requestIds.current(), Map.of("locationCount", requested.size()));
        return currentVersion + 1;
    }

    private MerchantAccess requireForRole(UUID merchantId, MerchantRole role) {
        return access.require(merchantId, role == MerchantRole.OWNER
                ? MerchantCapability.GRANT_OWNER : MerchantCapability.MANAGE_MEMBERS);
    }

    private MerchantMembershipEntity activeMember(UUID merchantId, UUID memberId) {
        return memberships.findByIdAndMerchantProfileIdAndMembershipStatus(memberId, merchantId, MembershipStatus.ACTIVE)
                .orElseThrow(() -> new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
    }

    private void requireOwnerForOwnerRole(MerchantAccess actorAccess, MerchantRole oldRole, MerchantRole newRole) {
        if ((oldRole == MerchantRole.OWNER || newRole == MerchantRole.OWNER) && actorAccess.role() != MerchantRole.OWNER) {
            throw new ApiException(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED, HttpStatus.FORBIDDEN,
                    "Merchant action is not allowed.");
        }
    }

    private void requireAnotherOwner(UUID merchantId) {
        memberships.findActiveOwnersForUpdate(merchantId);
        long owners = memberships.countByMerchantProfileIdAndRoleAndMembershipStatus(
                merchantId, MerchantRole.OWNER, MembershipStatus.ACTIVE);
        if (owners <= 1) throw new ApiException(ErrorCode.MERCHANT_LAST_OWNER_REQUIRED, HttpStatus.CONFLICT,
                "Merchant must keep at least one active owner.");
    }

    private MerchantMemberResponse response(MerchantMembershipEntity member) {
        String email = identities.findAllByUserId(member.getUserId()).stream()
                .filter(identity -> identity.getProvider() == vn.covie.users.model.IdentityProvider.EMAIL)
                .map(UserIdentityEntity::getEmailNormalized).findFirst().orElse(null);
        return response(member, email);
    }

    private static MerchantMemberResponse response(MerchantMembershipEntity member, String email) {
        return new MerchantMemberResponse(member.getId(), member.getUserId(), email, member.getRole(),
                member.getMembershipStatus(), member.getVersion());
    }
}
