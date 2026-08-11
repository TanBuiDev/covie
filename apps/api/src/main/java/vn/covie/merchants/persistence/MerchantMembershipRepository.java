package vn.covie.merchants.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.merchants.model.MembershipStatus;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantRole;

public interface MerchantMembershipRepository extends JpaRepository<MerchantMembershipEntity, UUID> {
    @Query("""
            select m from MerchantMembershipEntity m
            where m.merchantProfileId = :merchantId
              and m.userId = :userId
              and m.membershipStatus = vn.covie.merchants.model.MembershipStatus.ACTIVE
            """)
    Optional<MerchantMembershipEntity> findActive(@Param("merchantId") UUID merchantId, @Param("userId") UUID userId);

    long countByMerchantProfileIdAndRoleAndMembershipStatus(
            UUID merchantId, MerchantRole role, MembershipStatus status);
}
