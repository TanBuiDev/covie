package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.CatalogItemEntity;
public interface CatalogItemRepository extends JpaRepository<CatalogItemEntity,UUID> { Optional<CatalogItemEntity> findByIdAndMerchantProfileIdAndDeletedAtIsNull(UUID id,UUID merchantProfileId); boolean existsByMerchantProfileIdAndSlugAndDeletedAtIsNull(UUID merchantProfileId,String slug); }
