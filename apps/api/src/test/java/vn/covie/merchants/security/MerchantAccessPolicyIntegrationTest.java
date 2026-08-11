package vn.covie.merchants.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.merchants.model.MembershipStatus;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantMemberLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.security.SystemRole;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MerchantAccessPolicyIntegrationTest extends PostgresIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-12T00:00:00Z");

    @Autowired UserRepository users;
    @Autowired MerchantProfileRepository profiles;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired MerchantMemberLocationRepository memberLocations;
    @Autowired MerchantLocationRepository locations;

    @Test
    void ownerCanEditProfile() {
        Fixture fixture = fixture(MerchantRole.OWNER);

        MerchantAccess access = fixture.policy().require(fixture.merchantId(), MerchantCapability.EDIT_PROFILE);

        assertThat(access.role()).isEqualTo(MerchantRole.OWNER);
    }

    @Test
    void managerCanEditProfileButCannotGrantOwner() {
        Fixture fixture = fixture(MerchantRole.MANAGER);

        assertThat(fixture.policy().require(fixture.merchantId(), MerchantCapability.EDIT_PROFILE).role())
                .isEqualTo(MerchantRole.MANAGER);
        assertForbidden(() -> fixture.policy().require(fixture.merchantId(), MerchantCapability.GRANT_OWNER));
    }

    @Test
    void staffCannotEditProfile() {
        Fixture fixture = fixture(MerchantRole.STAFF);

        assertForbidden(() -> fixture.policy().require(fixture.merchantId(), MerchantCapability.EDIT_PROFILE));
    }

    @Test
    void staffAssignedToLocationCanManageOfferAtThatLocation() {
        Fixture fixture = fixture(MerchantRole.STAFF);
        MerchantLocationEntity assigned = location(fixture.merchantId(), "Location A");
        locations.saveAndFlush(assigned);
        memberLocations.saveAndFlush(fixture.membership().assignTo(assigned.getId(), NOW));

        MerchantAccess access = fixture.policy().requireLocation(
                fixture.merchantId(), assigned.getId(), MerchantCapability.MANAGE_LOCATION_OFFER);

        assertThat(access.membershipId()).isEqualTo(fixture.membership().getId());
    }

    @Test
    void staffCannotDiscoverOrManageUnassignedLocation() {
        Fixture fixture = fixture(MerchantRole.STAFF);
        MerchantLocationEntity assigned = location(fixture.merchantId(), "Location A");
        MerchantLocationEntity unassigned = location(fixture.merchantId(), "Location B");
        locations.saveAllAndFlush(java.util.List.of(assigned, unassigned));
        memberLocations.saveAndFlush(fixture.membership().assignTo(assigned.getId(), NOW));

        assertNotFound(() -> fixture.policy().requireLocation(
                fixture.merchantId(), unassigned.getId(), MerchantCapability.MANAGE_LOCATION_OFFER));
    }

    @Test
    void revokedMembershipTakesEffectOnTheNextRequest() {
        Fixture fixture = fixture(MerchantRole.MANAGER);
        fixture.policy().require(fixture.merchantId(), MerchantCapability.EDIT_PROFILE);
        fixture.membership().revoke();
        memberships.saveAndFlush(fixture.membership());

        assertNotFound(() -> fixture.policy().require(fixture.merchantId(), MerchantCapability.EDIT_PROFILE));
    }

    @Test
    void locationFromAnotherMerchantIsNotDiscoverable() {
        Fixture fixture = fixture(MerchantRole.OWNER);
        UUID otherMerchantId = profile("Other merchant");
        MerchantLocationEntity otherLocation = location(otherMerchantId, "Other location");
        locations.saveAndFlush(otherLocation);

        assertNotFound(() -> fixture.policy().requireLocation(
                fixture.merchantId(), otherLocation.getId(), MerchantCapability.MANAGE_LOCATION_OFFER));
    }

    @ParameterizedTest
    @MethodSource("capabilityMatrix")
    void capabilityMatrixIsAppliedExactly(MerchantRole role, MerchantCapability capability, boolean allowed) {
        assertThat(MerchantAccessPolicy.allows(role, capability)).isEqualTo(allowed);
    }

    private static Stream<Arguments> capabilityMatrix() {
        return Stream.of(MerchantRole.values()).flatMap(role -> Stream.of(MerchantCapability.values())
                .map(capability -> Arguments.of(role, capability, expected(role, capability))));
    }

    private static boolean expected(MerchantRole role, MerchantCapability capability) {
        if (role == MerchantRole.OWNER) return true;
        if (role == MerchantRole.MANAGER) return capability != MerchantCapability.GRANT_OWNER;
        return capability == MerchantCapability.VIEW_MERCHANT
                || capability == MerchantCapability.MANAGE_LOCATION_OFFER
                || capability == MerchantCapability.UPLOAD_MERCHANT_MEDIA;
    }

    private Fixture fixture(MerchantRole role) {
        UUID userId = activeUser();
        UUID merchantId = profile("Merchant " + UUID.randomUUID());
        MerchantMembershipEntity membership = MerchantMembershipEntity.active(
                UUID.randomUUID(), merchantId, userId, role, NOW);
        memberships.saveAndFlush(membership);
        return new Fixture(merchantId, membership, policyFor(userId));
    }

    private UUID activeUser() {
        UUID userId = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(userId, "Merchant access user", NOW));
        return userId;
    }

    private UUID profile(String name) {
        MerchantProfileEntity profile = MerchantProfileEntity.create(UUID.randomUUID(), name, name.toLowerCase().replace(' ', '-'), NOW);
        profiles.saveAndFlush(profile);
        return profile.getId();
    }

    private MerchantLocationEntity location(UUID merchantId, String name) {
        return MerchantLocationEntity.create(UUID.randomUUID(), merchantId, name, "1 CoVie Street", 10.0, 106.0, NOW);
    }

    private MerchantAccessPolicy policyFor(UUID userId) {
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.currentActor()).thenReturn(new CurrentActor(userId, Set.of(SystemRole.USER), UUID.randomUUID()));
        return new MerchantAccessPolicy(actors, memberships, memberLocations, locations);
    }

    private void assertForbidden(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(ApiException.class).satisfies(ex -> {
            ApiException api = (ApiException) ex;
            assertThat(api.code()).isEqualTo(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED);
            assertThat(api.status()).hasToString("403 FORBIDDEN");
        });
    }

    private void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOf(ApiException.class).satisfies(ex -> {
            ApiException api = (ApiException) ex;
            assertThat(api.code()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND);
            assertThat(api.status()).hasToString("404 NOT_FOUND");
        });
    }

    private record Fixture(UUID merchantId, MerchantMembershipEntity membership, MerchantAccessPolicy policy) {}
}
