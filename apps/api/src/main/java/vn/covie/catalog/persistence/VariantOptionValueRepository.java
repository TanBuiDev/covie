package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.*;
public interface VariantOptionValueRepository extends JpaRepository<VariantOptionValueEntity,VariantOptionValueId> { boolean existsByIdVariantIdAndIdOptionValueId(UUID variantId,UUID optionValueId); List<VariantOptionValueEntity> findAllByIdVariantId(UUID variantId); List<VariantOptionValueEntity> findAllByIdVariantIdIn(Collection<UUID> variantIds); void deleteByIdVariantIdAndIdOptionValueId(UUID variantId,UUID optionValueId); void deleteByIdVariantId(UUID variantId); }
