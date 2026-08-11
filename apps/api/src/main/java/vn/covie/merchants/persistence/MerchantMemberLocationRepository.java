package vn.covie.merchants.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.merchants.model.MerchantMemberLocationEntity;

public interface MerchantMemberLocationRepository extends JpaRepository<MerchantMemberLocationEntity, UUID> {
    boolean existsByMembershipIdAndMerchantLocationId(UUID membershipId, UUID locationId);
    List<MerchantMemberLocationEntity> findAllByMembershipId(UUID membershipId);
    void deleteAllByMembershipId(UUID membershipId);
}
