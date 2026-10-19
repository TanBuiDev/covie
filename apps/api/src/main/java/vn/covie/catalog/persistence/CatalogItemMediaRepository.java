package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.media.model.MediaAssetEntity;
public interface CatalogItemMediaRepository extends JpaRepository<CatalogItemMediaEntity,CatalogItemMediaId> { boolean existsByIdCatalogItemIdAndIdMediaAssetId(UUID catalogItemId,UUID mediaAssetId); void deleteByIdCatalogItemIdAndIdMediaAssetId(UUID catalogItemId,UUID mediaAssetId); void deleteByIdCatalogItemId(UUID catalogItemId); @Query("select asset from CatalogItemMediaEntity itemMedia join itemMedia.mediaAsset asset where itemMedia.id.catalogItemId = :catalogItemId order by itemMedia.primary desc, itemMedia.sortOrder asc, asset.id asc") List<MediaAssetEntity> findAllMediaAssetsByCatalogItemId(@Param("catalogItemId") UUID catalogItemId); @Query("select itemMedia from CatalogItemMediaEntity itemMedia join fetch itemMedia.mediaAsset asset where itemMedia.id.catalogItemId = :catalogItemId order by itemMedia.primary desc, itemMedia.sortOrder asc, asset.id asc") List<CatalogItemMediaEntity> findAllAssociationsByCatalogItemId(@Param("catalogItemId") UUID catalogItemId);
    default List<CatalogItemMediaEntity> findAllGalleryAssociationsByCatalogItemId(UUID itemId) {
        var associations = findAllAssociationsByCatalogItemId(itemId);
        if (associations.stream().noneMatch(entry -> "VIDEO".equals(entry.getMediaType()))) return associations;
        return associations.stream().sorted(Comparator.comparingInt(CatalogItemMediaEntity::getSortOrder)
                .thenComparing(entry -> entry.getId().getMediaAssetId())).toList();
    }
}
