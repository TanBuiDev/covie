package vn.covie.notifications.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.merchants.model.MerchantMemberLocationEntity;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.persistence.MerchantMemberLocationRepository;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

class MerchantNotificationRecipientResolverIntegrationTest extends PostgresIntegrationTest {
    @Autowired MerchantNotificationRecipientResolver resolver; @Autowired UserRepository users;
    @Autowired MerchantProfileRepository profiles; @Autowired MerchantLocationRepository locations;
    @Autowired MerchantMembershipRepository memberships; @Autowired MerchantMemberLocationRepository assignments;

    @Test @Transactional
    void ownerAlwaysReceivesAndStaffOnlyReceivesForAssignedLocation() {
        Instant now=Instant.now(); UUID merchant=UUID.randomUUID();
        UUID owner=UUID.randomUUID(), staff=UUID.randomUUID(); users.saveAllAndFlush(java.util.List.of(UserEntity.active(owner,"owner",now),UserEntity.active(staff,"staff",now)));
        profiles.saveAndFlush(MerchantProfileEntity.create(merchant,"Merchant","merchant-"+merchant,now));
        var first=locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(),merchant,"First","A",10.8,106.7,now));
        var second=locations.saveAndFlush(MerchantLocationEntity.create(UUID.randomUUID(),merchant,"Second","B",10.9,106.8,now));
        var ownerMembership=memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),merchant,owner,MerchantRole.OWNER,now));
        var staffMembership=memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(),merchant,staff,MerchantRole.STAFF,now));
        assignments.saveAndFlush(MerchantMemberLocationEntity.assign(UUID.randomUUID(),staffMembership.getId(),first.getId(),now));
        assertThat(resolver.processorsForLocation(merchant,first.getId())).containsExactlyInAnyOrder(owner,staff);
        assertThat(resolver.processorsForLocation(merchant,second.getId())).containsExactly(owner);
    }
}
