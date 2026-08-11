package vn.covie.merchants.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.merchants.api.dto.AddMerchantMemberRequest;
import vn.covie.merchants.api.dto.MerchantMemberResponse;
import vn.covie.merchants.api.dto.UpdateMerchantMemberRequest;
import vn.covie.merchants.application.MerchantMembershipService;
import vn.covie.merchants.model.MembershipStatus;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantMemberLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.security.MerchantAccess;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;
import vn.covie.users.model.UserIdentityEntity;
import vn.covie.users.persistence.UserIdentityRepository;
import vn.covie.users.security.EmailNormalizer;

class MerchantMembershipControllerIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void addsExistingLocalEmailAsActiveMember() {
        UUID merchantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        MerchantAccessPolicy access = mock(MerchantAccessPolicy.class);
        when(access.require(merchantId, MerchantCapability.MANAGE_MEMBERS))
                .thenReturn(new MerchantAccess(actorId, merchantId, UUID.randomUUID(), MerchantRole.MANAGER));
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        UserIdentityEntity identity = UserIdentityEntity.email(UUID.randomUUID(), userId, "member@covie.vn", "hash", NOW);
        when(identities.findLocalEmail("member@covie.vn")).thenReturn(Optional.of(identity));
        MerchantMembershipRepository memberships = mock(MerchantMembershipRepository.class);
        when(memberships.findActiveOrInvited(merchantId, userId)).thenReturn(Optional.empty());
        when(memberships.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        MerchantMembershipService service = service(access, memberships, mock(MerchantMemberLocationRepository.class), identities);

        MerchantMemberResponse response = service.add(merchantId,
                new AddMerchantMemberRequest(" MEMBER@covie.vn ", MerchantRole.STAFF));

        assertThat(response.userId()).isEqualTo(userId);
        assertThat(response.role()).isEqualTo(MerchantRole.STAFF);
        assertThat(response.status()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void managerCannotAlterAnOwnerMembership() {
        UUID merchantId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        MerchantAccessPolicy access = mock(MerchantAccessPolicy.class);
        when(access.require(merchantId, MerchantCapability.MANAGE_MEMBERS))
                .thenReturn(new MerchantAccess(UUID.randomUUID(), merchantId, UUID.randomUUID(), MerchantRole.MANAGER));
        MerchantMembershipRepository memberships = mock(MerchantMembershipRepository.class);
        when(memberships.findByIdAndMerchantProfileIdAndMembershipStatus(memberId, merchantId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(MerchantMembershipEntity.active(memberId, merchantId, UUID.randomUUID(), MerchantRole.OWNER, NOW)));
        MerchantMembershipService service = service(access, memberships, mock(MerchantMemberLocationRepository.class), mock(UserIdentityRepository.class));

        assertThatThrownBy(() -> service.update(merchantId, memberId, "\"v0\"", new UpdateMerchantMemberRequest(MerchantRole.MANAGER)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED);
    }

    @Test
    void cannotDemoteTheLastActiveOwner() {
        UUID merchantId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        MerchantAccessPolicy access = mock(MerchantAccessPolicy.class);
        when(access.require(merchantId, MerchantCapability.MANAGE_MEMBERS))
                .thenReturn(new MerchantAccess(UUID.randomUUID(), merchantId, UUID.randomUUID(), MerchantRole.OWNER));
        MerchantMembershipRepository memberships = mock(MerchantMembershipRepository.class);
        MerchantMembershipEntity owner = MerchantMembershipEntity.active(memberId, merchantId, UUID.randomUUID(), MerchantRole.OWNER, NOW);
        when(memberships.findByIdAndMerchantProfileIdAndMembershipStatus(memberId, merchantId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(owner));
        when(memberships.findActiveOwnersForUpdate(merchantId)).thenReturn(List.of(owner));
        when(memberships.countByMerchantProfileIdAndRoleAndMembershipStatus(
                merchantId, MerchantRole.OWNER, MembershipStatus.ACTIVE)).thenReturn(1L);
        MerchantMembershipService service = service(access, memberships, mock(MerchantMemberLocationRepository.class), mock(UserIdentityRepository.class));

        assertThatThrownBy(() -> service.update(merchantId, memberId, "\"v0\"", new UpdateMerchantMemberRequest(MerchantRole.MANAGER)))
                .isInstanceOf(ApiException.class)
                .extracting(exception -> ((ApiException) exception).code())
                .isEqualTo(ErrorCode.MERCHANT_LAST_OWNER_REQUIRED);
        verify(memberships).findActiveOwnersForUpdate(merchantId);
    }

    @Test
    void changingStaffRoleClearsItsLocationAssignments() {
        UUID merchantId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        MerchantAccessPolicy access = mock(MerchantAccessPolicy.class);
        when(access.require(merchantId, MerchantCapability.MANAGE_MEMBERS))
                .thenReturn(new MerchantAccess(UUID.randomUUID(), merchantId, UUID.randomUUID(), MerchantRole.OWNER));
        MerchantMembershipRepository memberships = mock(MerchantMembershipRepository.class);
        MerchantMembershipEntity staff = MerchantMembershipEntity.active(memberId, merchantId, UUID.randomUUID(), MerchantRole.STAFF, NOW);
        when(memberships.findByIdAndMerchantProfileIdAndMembershipStatus(memberId, merchantId, MembershipStatus.ACTIVE))
                .thenReturn(Optional.of(staff));
        when(memberships.saveAndFlush(staff)).thenReturn(staff);
        MerchantMemberLocationRepository locations = mock(MerchantMemberLocationRepository.class);
        UserIdentityRepository identities = mock(UserIdentityRepository.class);
        when(identities.findAllByUserId(staff.getUserId())).thenReturn(List.of());
        MerchantMembershipService service = service(access, memberships, locations, identities);

        service.update(merchantId, memberId, "\"v0\"", new UpdateMerchantMemberRequest(MerchantRole.MANAGER));

        verify(locations).deleteAllByMembershipId(memberId);
    }

    private MerchantMembershipService service(MerchantAccessPolicy access, MerchantMembershipRepository memberships,
            MerchantMemberLocationRepository locations, UserIdentityRepository identities) {
        return new MerchantMembershipService(mock(CurrentActorProvider.class), access, memberships, locations, identities,
                new EmailNormalizer(), new UuidV7Generator(), fixedClock(), mock(AuditLogWriter.class), mock(RequestIdAccessor.class));
    }

    private static AppClock fixedClock() { return () -> NOW; }
}
