package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.covie.catalog.model.LocationVariantEntity;
public interface LocationVariantRepository extends JpaRepository<LocationVariantEntity,UUID> {
    Optional<LocationVariantEntity> findByIdAndDeletedAtIsNull(UUID id);
    @Query("""
            select lv from LocationVariantEntity lv
            join VariantEntity v on v.id = lv.variantId
            join CatalogItemEntity i on i.id = v.catalogItemId
            where lv.id = :id
              and lv.deletedAt is null
              and v.deletedAt is null
              and i.deletedAt is null
            """)
    Optional<LocationVariantEntity> findUsableById(@Param("id") UUID id);
}
