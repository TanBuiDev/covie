package vn.covie.shipping.persistence;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.covie.shipping.model.*;
public interface ShippingPolicyRepository extends JpaRepository<ShippingPolicyEntity, UUID> {
    List<ShippingPolicyEntity> findByMerchantLocationIdOrderById(UUID locationId);
    Optional<ShippingPolicyEntity> findByIdAndMerchantLocationId(UUID id, UUID locationId);
    Optional<ShippingPolicyEntity> findFirstByMerchantLocationIdAndPolicyStatus(UUID locationId, ShippingPolicyStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select p from ShippingPolicyEntity p where p.id=:id and p.merchantLocationId=:locationId") Optional<ShippingPolicyEntity> findForUpdate(@Param("id") UUID id,@Param("locationId") UUID locationId);
}
