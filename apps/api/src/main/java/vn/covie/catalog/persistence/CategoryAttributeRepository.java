package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.CategoryAttributeEntity;
public interface CategoryAttributeRepository extends JpaRepository<CategoryAttributeEntity,UUID> { List<CategoryAttributeEntity> findAllByCategoryIdOrderBySortOrderAscNameAscIdAsc(UUID categoryId); }
