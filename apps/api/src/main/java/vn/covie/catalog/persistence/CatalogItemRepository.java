package vn.covie.catalog.persistence;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.CatalogItemEntity;
public interface CatalogItemRepository extends JpaRepository<CatalogItemEntity,UUID> { Optional<CatalogItemEntity> findByIdAndMerchantProfileIdAndDeletedAtIsNull(UUID id,UUID merchantProfileId); @Lock(LockModeType.PESSIMISTIC_WRITE) Optional<CatalogItemEntity> findLockedByIdAndMerchantProfileIdAndDeletedAtIsNull(UUID id,UUID merchantProfileId); List<CatalogItemEntity> findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(UUID merchantProfileId); boolean existsByMerchantProfileIdAndSlugAndDeletedAtIsNull(UUID merchantProfileId,String slug); }
