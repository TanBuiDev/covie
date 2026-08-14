package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.*;
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {
    List<CategoryEntity> findAllByParentIdIsNullAndStatusAndCatalogTypeInOrderBySortOrderAscNameAscIdAsc(CategoryStatus status, Collection<CategoryCatalogType> types);
    List<CategoryEntity> findAllByParentIdAndStatusAndCatalogTypeInOrderBySortOrderAscNameAscIdAsc(UUID parentId, CategoryStatus status, Collection<CategoryCatalogType> types);
    Optional<CategoryEntity> findByIdAndStatusAndDeletedAtIsNull(UUID id, CategoryStatus status);
}
