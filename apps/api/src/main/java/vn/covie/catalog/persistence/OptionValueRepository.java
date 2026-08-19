package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.OptionValueEntity;
public interface OptionValueRepository extends JpaRepository<OptionValueEntity,UUID> { List<OptionValueEntity> findAllByOptionGroupIdInOrderBySortOrderAscIdAsc(Collection<UUID> optionGroupIds); }
