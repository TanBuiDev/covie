package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.*;
public interface CatalogItemTagRepository extends JpaRepository<CatalogItemTagEntity,CatalogItemTagId> { boolean existsByIdCatalogItemIdAndIdTagId(UUID catalogItemId,UUID tagId); void deleteByIdCatalogItemIdAndIdTagId(UUID catalogItemId,UUID tagId); void deleteByIdCatalogItemId(UUID catalogItemId); @org.springframework.data.jpa.repository.Query("select tag.name from CatalogItemTagEntity itemTag join itemTag.tag tag where itemTag.id.catalogItemId = :catalogItemId order by tag.normalizedName asc, tag.id asc") List<String> findTagNamesByCatalogItemId(@org.springframework.data.repository.query.Param("catalogItemId") UUID catalogItemId); }
