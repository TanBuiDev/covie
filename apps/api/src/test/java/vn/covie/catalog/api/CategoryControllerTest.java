package vn.covie.catalog.api;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import vn.covie.catalog.application.CategoryQueryService;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CategoryAttributeType;
import vn.covie.catalog.model.CategoryCatalogType;

class CategoryControllerTest {
    private CategoryQueryService categories;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        categories = mock(CategoryQueryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CategoryController(categories)).build();
    }

    @Test
    void lists_product_categories_under_the_requested_parent() throws Exception {
        UUID parentId = UUID.randomUUID();
        UUID id = UUID.randomUUID();
        when(categories.list(parentId, CatalogItemType.PRODUCT)).thenReturn(List.of(
                new CategoryQueryService.CategorySummary(id, parentId, "Tea", "tea", CategoryCatalogType.BOTH, 3)));

        mockMvc.perform(get("/api/v1/categories").param("parentId", parentId.toString()).param("type", "PRODUCT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(id.toString()))
                .andExpect(jsonPath("$[0].parentId").value(parentId.toString()))
                .andExpect(jsonPath("$[0].catalogType").value("BOTH"))
                .andExpect(jsonPath("$[0].sortOrder").value(3));

        verify(categories).list(eq(parentId), eq(CatalogItemType.PRODUCT));
    }

    @Test
    void returns_attribute_definitions_only_from_the_category_detail_endpoint() throws Exception {
        UUID categoryId = UUID.randomUUID();
        UUID attributeId = UUID.randomUUID();
        UUID optionId = UUID.randomUUID();
        when(categories.detail(categoryId)).thenReturn(new CategoryQueryService.CategoryDetail(
                categoryId, null, "Coffee", "coffee", CategoryCatalogType.PRODUCT, 0,
                List.of(new CategoryQueryService.AttributeDefinition(attributeId, "Roast", "roast", CategoryAttributeType.SINGLE_SELECT,
                        true, true, 1, List.of(new CategoryQueryService.AttributeOption(optionId, "Dark", 0))))));

        mockMvc.perform(get("/api/v1/categories/{categoryId}", categoryId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(categoryId.toString()))
                .andExpect(jsonPath("$.attributes[0].attributeKey").value("roast"))
                .andExpect(jsonPath("$.attributes[0].dataType").value("SINGLE_SELECT"))
                .andExpect(jsonPath("$.attributes[0].options[0].id").value(optionId.toString()))
                .andExpect(jsonPath("$.attributes[0].options[0].value").value("Dark"));
    }
}
