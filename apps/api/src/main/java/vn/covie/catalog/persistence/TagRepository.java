package vn.covie.catalog.persistence;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.catalog.model.TagEntity;
public interface TagRepository extends JpaRepository<TagEntity,UUID> { Optional<TagEntity> findByNormalizedName(String normalizedName); }
