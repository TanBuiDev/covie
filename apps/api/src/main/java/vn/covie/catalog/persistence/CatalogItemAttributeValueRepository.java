package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.CatalogItemAttributeValueEntity;
public interface CatalogItemAttributeValueRepository extends JpaRepository<CatalogItemAttributeValueEntity,UUID> { List<CatalogItemAttributeValueEntity> findAllByCatalogItemId(UUID catalogItemId); void deleteByCatalogItemId(UUID catalogItemId); }
