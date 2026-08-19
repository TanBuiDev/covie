package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.VariantEntity;
public interface VariantRepository extends JpaRepository<VariantEntity,UUID> { List<VariantEntity> findAllByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAscIdAsc(UUID catalogItemId); Optional<VariantEntity> findByIdAndCatalogItemIdAndDeletedAtIsNull(UUID id, UUID catalogItemId); long countByCatalogItemIdAndDeletedAtIsNull(UUID catalogItemId); }
