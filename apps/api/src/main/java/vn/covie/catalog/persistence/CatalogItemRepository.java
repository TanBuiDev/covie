package vn.covie.catalog.persistence;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import vn.covie.catalog.model.CatalogItemEntity;
public interface CatalogItemRepository extends JpaRepository<CatalogItemEntity,UUID>{Optional<CatalogItemEntity> findByIdAndMerchantProfileIdAndDeletedAtIsNull(UUID id,UUID merchantProfileId);@Lock(LockModeType.PESSIMISTIC_WRITE)Optional<CatalogItemEntity> findLockedByIdAndMerchantProfileIdAndDeletedAtIsNull(UUID id,UUID merchantProfileId);@Lock(LockModeType.PESSIMISTIC_WRITE)@Query("select i from CatalogItemEntity i where i.id=:id and i.deletedAt is null")Optional<CatalogItemEntity> lockById(@Param("id")UUID id);List<CatalogItemEntity> findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(UUID merchantProfileId);boolean existsByMerchantProfileIdAndSlugAndDeletedAtIsNull(UUID merchantProfileId,String slug);}
