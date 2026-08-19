package vn.covie.inventory.persistence;
import java.util.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import vn.covie.inventory.model.InventoryBalanceEntity;
public interface InventoryBalanceRepository extends JpaRepository<InventoryBalanceEntity,UUID>{ @Query(value="select * from inventory_balances where location_variant_id in (:ids) order by location_variant_id asc for update",nativeQuery=true) List<InventoryBalanceEntity> lockAllByLocationVariantIds(@Param("ids")Collection<UUID> ids); }
