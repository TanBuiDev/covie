package vn.covie.merchants.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.merchants.model.MerchantMemberLocationEntity;

public interface MerchantMemberLocationRepository extends JpaRepository<MerchantMemberLocationEntity, UUID> {
    @Query("""
            select count(assignment) > 0
            from MerchantMemberLocationEntity assignment
            join MerchantMembershipEntity membership on membership.id = assignment.membershipId
            join MerchantLocationEntity location on location.id = assignment.merchantLocationId
            where assignment.membershipId = :membershipId
              and location.merchantProfileId = membership.merchantProfileId
              and location.deletedAt is null
            """)
    boolean existsActiveLocationAssignment(@Param("membershipId") UUID membershipId);

    boolean existsByMembershipIdAndMerchantLocationId(UUID membershipId, UUID locationId);
    List<MerchantMemberLocationEntity> findAllByMembershipId(UUID membershipId);
    void deleteAllByMembershipId(UUID membershipId);
}
