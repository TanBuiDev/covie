package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.*;
public interface CatalogItemMediaRepository extends JpaRepository<CatalogItemMediaEntity,CatalogItemMediaId> { boolean existsByIdCatalogItemIdAndIdMediaAssetId(UUID catalogItemId,UUID mediaAssetId); void deleteByIdCatalogItemIdAndIdMediaAssetId(UUID catalogItemId,UUID mediaAssetId); void deleteByIdCatalogItemId(UUID catalogItemId); }
