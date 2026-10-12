package vn.covie.catalog.api;

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

@AutoConfigureMockMvc @Transactional
class CategoryHierarchyHttpIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired com.fasterxml.jackson.databind.ObjectMapper json;

    @Test void serializesOnlySelectedCategoryAttributesAndOrderedOptions() throws Exception {
        UUID selected = insert(null, "Selected", "PRODUCT", "ACTIVE", false, 0);
        UUID other = insert(null, "Other", "PRODUCT", "ACTIVE", false, 0);
        UUID choice = attribute(selected, "Choice", "choice", "SINGLE_SELECT", 1);
        UUID text = attribute(selected, "Text", "text", "TEXT", 2);
        UUID foreign = attribute(other, "Foreign", "private_sentinel", "TEXT", 0);
        option(choice, "Beta", 1); option(choice, "Alpha", 1); option(choice, "First", 0);
        option(foreign, "FOREIGN_SENTINEL", 0);
        var before = jdbc.queryForList("select * from category_attribute_options order by id");
        var response = http.perform(get("/api/v1/categories/" + selected)).andExpect(status().isOk()).andReturn().getResponse();
        var detail = json.readTree(response.getContentAsString());
        assertThat(response.getContentAsString()).doesNotContain("FOREIGN_SENTINEL", "private_sentinel");
        assertThat(detail.path("attributes").size()).isEqualTo(2);
        assertThat(detail.path("attributes").get(0).path("id").asText()).isEqualTo(choice.toString());
        assertThat(detail.path("attributes").get(1).path("id").asText()).isEqualTo(text.toString());
        var values = new java.util.ArrayList<String>();
        detail.path("attributes").get(0).path("options").forEach(row -> values.add(row.path("value").asText()));
        assertThat(values).containsExactly("First", "Alpha", "Beta");
        assertThat(detail.path("attributes").get(1).path("options").isEmpty()).isTrue();
        assertThat(jdbc.queryForList("select * from category_attribute_options order by id")).isEqualTo(before);
    }

    private UUID attribute(UUID category, String name, String key, String type, int sort) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into category_attributes(id,category_id,name,attribute_key,data_type,is_required,is_filterable,sort_order) values (?,?,?,?,?,true,true,?)", id, category, name, key, type, sort);
        return id;
    }
    private void option(UUID attribute, String value, int sort) {
        jdbc.update("insert into category_attribute_options(id,category_attribute_id,option_value,sort_order) values (?,?,?,?)", UUID.randomUUID(), attribute, value, sort);
    }

    @Test void filtersChildrenByCompatibleTypeStatusAndDeletionWithoutWrites() throws Exception {
        UUID root = insert(null, "Root", "BOTH", "ACTIVE", false, 0);
        UUID product = insert(root, "Product", "PRODUCT", "ACTIVE", false, 2);
        UUID service = insert(root, "Service", "SERVICE", "ACTIVE", false, 3);
        UUID both = insert(root, "Both", "BOTH", "ACTIVE", false, 1);
        insert(root, "Inactive", "BOTH", "INACTIVE", false, 0);
        UUID deleted = insert(root, "Deleted", "BOTH", "ACTIVE", true, 0);
        UUID foreign = insert(null, "Other", "BOTH", "ACTIVE", false, 0);
        insert(foreign, "Other child", "BOTH", "ACTIVE", false, 0);
        var before = jdbc.queryForList("select * from categories order by id");
        assertThat(ids(root, null)).containsExactly(both.toString(), product.toString(), service.toString());
        assertThat(ids(root, "PRODUCT")).containsExactly(both.toString(), product.toString());
        assertThat(ids(root, "SERVICE")).containsExactly(both.toString(), service.toString());
        assertThat(ids(product, null)).isEmpty();
        http.perform(get("/api/v1/categories/" + deleted)).andExpect(status().isNotFound());
        assertThat(jdbc.queryForList("select * from categories order by id")).isEqualTo(before);
    }
    private java.util.List<String> ids(UUID parent, String type) throws Exception {
        var request = get("/api/v1/categories").param("parentId", parent.toString());
        if (type != null) request.param("type", type);
        var response = http.perform(request).andExpect(status().isOk()).andReturn().getResponse();
        var result = new java.util.ArrayList<String>();
        json.readTree(response.getContentAsString()).forEach(row -> result.add(row.path("id").asText()));
        return result;
    }
    private UUID insert(UUID parent, String name, String type, String state, boolean deleted, int sort) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into categories(id,parent_id,name,slug,catalog_type,status,sort_order,deleted_at,version) values (?,?,?,?,?,?,?," + (deleted ? "now()" : "null") + ",0)", id,parent,name,id.toString(),type,state,sort);
        return id;
    }
}
