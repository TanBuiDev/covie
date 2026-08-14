package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.CategoryAttributeOptionEntity;
public interface CategoryAttributeOptionRepository extends JpaRepository<CategoryAttributeOptionEntity,UUID> { List<CategoryAttributeOptionEntity> findAllByCategoryAttributeIdInOrderBySortOrderAscOptionValueAscIdAsc(Collection<UUID> attributeIds); }
