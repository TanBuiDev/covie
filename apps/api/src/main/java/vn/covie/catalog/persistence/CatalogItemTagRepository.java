package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.*;
public interface CatalogItemTagRepository extends JpaRepository<CatalogItemTagEntity,CatalogItemTagId> { boolean existsByIdCatalogItemIdAndIdTagId(UUID catalogItemId,UUID tagId); void deleteByIdCatalogItemIdAndIdTagId(UUID catalogItemId,UUID tagId); void deleteByIdCatalogItemId(UUID catalogItemId); }
