package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.OptionGroupEntity;
public interface OptionGroupRepository extends JpaRepository<OptionGroupEntity,UUID> { List<OptionGroupEntity> findAllByCatalogItemIdOrderBySortOrderAscIdAsc(UUID catalogItemId); }
