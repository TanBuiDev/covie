package vn.covie.needs.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import vn.covie.needs.model.NeedType;
import vn.covie.support.PostgresIntegrationTest;

class CategoryHierarchyRepositoryIntegrationTest extends PostgresIntegrationTest {
    @Autowired CategoryHierarchyRepository categories;
    @Autowired JdbcTemplate jdbc;

    @Test void resolvesTypeCompatibilityAndDescendantOnly() {
        UUID product = category(null, "PRODUCT");
        UUID descendant = category(product, "BOTH");
        UUID service = category(null, "SERVICE");
        assertThat(categories.supportsNeedType(product, NeedType.PRODUCT)).isTrue();
        assertThat(categories.supportsNeedType(product, NeedType.SERVICE)).isFalse();
        assertThat(categories.supportsNeedType(service, NeedType.SERVICE)).isTrue();
        assertThat(categories.isSameOrDescendant(product, descendant)).isTrue();
        assertThat(categories.isSameOrDescendant(product, service)).isFalse();
    }

    private UUID category(UUID parent, String type) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into categories(id,parent_id,name,slug,catalog_type,status,sort_order,version) values (?,?,?,?,?,'ACTIVE',0,0)",
                id, parent, "Category", "category-" + id, type);
        return id;
    }
}
