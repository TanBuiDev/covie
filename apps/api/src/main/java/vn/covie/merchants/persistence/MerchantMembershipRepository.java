package vn.covie.merchants.persistence;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import vn.covie.merchants.model.MembershipStatus;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.api.dto.MerchantMemberResponse;
import vn.covie.merchants.api.dto.MerchantSummaryResponse;

public interface MerchantMembershipRepository extends JpaRepository<MerchantMembershipEntity, UUID> {
    @Query("""
            select m from MerchantMembershipEntity m
            where m.merchantProfileId = :merchantId
              and m.userId = :userId
              and m.membershipStatus = vn.covie.merchants.model.MembershipStatus.ACTIVE
            """)
    Optional<MerchantMembershipEntity> findActive(@Param("merchantId") UUID merchantId, @Param("userId") UUID userId);

    @Query("""
            select new vn.covie.merchants.api.dto.MerchantSummaryResponse(
                profile.id, profile.name, profile.slug, profile.merchantStatus, membership.role, profile.version)
            from MerchantMembershipEntity membership join MerchantProfileEntity profile
              on profile.id = membership.merchantProfileId
            where membership.userId = :userId
              and membership.membershipStatus = vn.covie.merchants.model.MembershipStatus.ACTIVE
              and profile.deletedAt is null
            order by profile.name asc, profile.id asc
            """)
    List<MerchantSummaryResponse> findActiveMerchantSummaries(@Param("userId") UUID userId);

    @Query("""
            select new vn.covie.merchants.api.dto.MerchantMemberResponse(
                membership.id, membership.userId, identity.emailNormalized, membership.role,
                membership.membershipStatus, membership.version)
            from MerchantMembershipEntity membership left join UserIdentityEntity identity
              on identity.userId = membership.userId
              and identity.provider = vn.covie.users.model.IdentityProvider.EMAIL
            where membership.merchantProfileId = :merchantId
              and membership.membershipStatus = vn.covie.merchants.model.MembershipStatus.ACTIVE
            order by membership.role asc, identity.emailNormalized asc, membership.id asc
            """)
    List<MerchantMemberResponse> findActiveMemberResponses(@Param("merchantId") UUID merchantId);

    Optional<MerchantMembershipEntity> findByIdAndMerchantProfileIdAndMembershipStatus(
            UUID id, UUID merchantId, MembershipStatus membershipStatus);

    @Query("""
            select membership from MerchantMembershipEntity membership
            where membership.merchantProfileId = :merchantId
              and membership.userId = :userId
              and membership.membershipStatus in (
                  vn.covie.merchants.model.MembershipStatus.ACTIVE,
                  vn.covie.merchants.model.MembershipStatus.INVITED)
            """)
    Optional<MerchantMembershipEntity> findActiveOrInvited(@Param("merchantId") UUID merchantId, @Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select membership from MerchantMembershipEntity membership
            where membership.merchantProfileId = :merchantId
              and membership.role = vn.covie.merchants.model.MerchantRole.OWNER
              and membership.membershipStatus = vn.covie.merchants.model.MembershipStatus.ACTIVE
            """)
    List<MerchantMembershipEntity> findActiveOwnersForUpdate(@Param("merchantId") UUID merchantId);

    long countByMerchantProfileIdAndRoleAndMembershipStatus(
            UUID merchantId, MerchantRole role, MembershipStatus status);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MerchantMembershipEntity membership set membership.version = membership.version + 1 where membership.id = :id and membership.version = :version")
    int incrementVersion(@Param("id") UUID id, @Param("version") long version);

    @Query("""
            select distinct membership.userId from MerchantMembershipEntity membership
            where membership.merchantProfileId=:merchantId
              and membership.membershipStatus=vn.covie.merchants.model.MembershipStatus.ACTIVE
              and (membership.role in (vn.covie.merchants.model.MerchantRole.OWNER, vn.covie.merchants.model.MerchantRole.MANAGER)
                   or (membership.role=vn.covie.merchants.model.MerchantRole.STAFF and exists (
                       select assignment.id from MerchantMemberLocationEntity assignment
                       where assignment.membershipId=membership.id and assignment.merchantLocationId=:locationId)))
            """)
    List<UUID> findActiveProcessorUserIds(@Param("merchantId") UUID merchantId, @Param("locationId") UUID locationId);

    @Query("""
            select membership.userId from MerchantMembershipEntity membership
            where membership.merchantProfileId = :merchantId
              and membership.membershipStatus = vn.covie.merchants.model.MembershipStatus.ACTIVE
              and membership.role in (vn.covie.merchants.model.MerchantRole.OWNER, vn.covie.merchants.model.MerchantRole.MANAGER)
            """)
    List<UUID> findActiveOwnerAndManagerUserIds(@Param("merchantId") UUID merchantId);
}
