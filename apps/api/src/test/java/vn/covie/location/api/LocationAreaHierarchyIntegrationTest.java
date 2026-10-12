package vn.covie.location.api;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.support.PostgresIntegrationTest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
class LocationAreaHierarchyIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    @Test
    void readsEachActiveLevelInOrderWithoutMutatingRows() throws Exception {
        UUID root = insert(null, "Root", 0, "ACTIVE");
        UUID sibling = insert(null, "Other root", 1, "ACTIVE");
        UUID alpha = insert(root, "Alpha", 2, "ACTIVE");
        UUID beta = insert(root, "Beta", 2, "ACTIVE");
        UUID first = insert(root, "First", 1, "ACTIVE");
        UUID leaf = insert(alpha, "Leaf", 0, "ACTIVE");
        UUID inactive = insert(root, "Inactive", 0, "INACTIVE");
        UUID otherChild = insert(sibling, "Other child", 0, "ACTIVE");
        var before = jdbc.queryForList("select * from location_areas order by id");
        var roots = ids(null);
        assertThat(roots).contains(root.toString(), sibling.toString()).doesNotContain(alpha.toString(), inactive.toString(), otherChild.toString());
        assertThat(ids(root)).containsExactly(first.toString(), alpha.toString(), beta.toString());
        assertThat(ids(alpha)).containsExactly(leaf.toString());
        assertThat(ids(leaf)).isEmpty();
        assertThat(ids(UUID.randomUUID())).isEmpty();
        assertThat(jdbc.queryForList("select * from location_areas order by id")).isEqualTo(before);
    }

    private java.util.List<String> ids(UUID parent) throws Exception {
        var request = get("/api/v1/location-areas");
        if (parent != null) request.param("parentId", parent.toString());
        var response = http.perform(request).andExpect(status().isOk()).andReturn().getResponse();
        var result = new java.util.ArrayList<String>();
        json.readTree(response.getContentAsString()).forEach(row -> result.add(row.path("id").asText()));
        return result;
    }

    private UUID insert(UUID parent, String name, int sort, String state) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into location_areas(id,parent_id,area_type,name,slug,status,sort_order,created_at,updated_at,version) "
                + "values (?,?,'COMMUNITY',?,?,?, ?,now(),now(),0)", id, parent, name, id.toString(), state, sort);
        return id;
    }
}
