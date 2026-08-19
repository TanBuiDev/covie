package vn.covie.shipping.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.shipping.model.ShippingRuleEntity;
public interface ShippingRuleRepository extends JpaRepository<ShippingRuleEntity, UUID> { List<ShippingRuleEntity> findByShippingPolicyIdOrderByPriority(UUID policyId); void deleteByShippingPolicyId(UUID policyId); }
