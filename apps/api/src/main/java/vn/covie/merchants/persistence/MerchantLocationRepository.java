package vn.covie.merchants.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.merchants.model.MerchantLocationEntity;

public interface MerchantLocationRepository extends JpaRepository<MerchantLocationEntity, UUID> {
    Optional<MerchantLocationEntity> findByIdAndMerchantProfileIdAndDeletedAtIsNull(UUID id, UUID merchantProfileId);

    List<MerchantLocationEntity> findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(UUID merchantId);

    @Query("""
            select location from MerchantMemberLocationEntity assignment
            join MerchantLocationEntity location on location.id = assignment.merchantLocationId
            where assignment.membershipId = :membershipId
              and location.merchantProfileId = :merchantId
              and location.deletedAt is null
            order by location.name asc, location.id asc
            """)
    List<MerchantLocationEntity> findAssignedActive(@Param("merchantId") UUID merchantId,
            @Param("membershipId") UUID membershipId);
}
