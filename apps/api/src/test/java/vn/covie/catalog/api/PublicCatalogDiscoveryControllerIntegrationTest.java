package vn.covie.catalog.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import vn.covie.support.PostgresIntegrationTest;

class PublicCatalogDiscoveryControllerIntegrationTest extends PostgresIntegrationTest {
    @Autowired WebApplicationContext web;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(web).apply(springSecurity()).build();
    }

    @Test
    void requiresAnExplicitLocationContextForPublicDiscovery() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/items"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LOCATION_CONTEXT_REQUIRED"));
    }

    @Test
    void rejectsUnknownAndInvalidCatalogFiltersWith422() throws Exception {
        mockMvc.perform(get("/api/v1/catalog/items").param("lat", "10.8").param("lng", "106.7")
                        .param("type", "UNKNOWN"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CATALOG_FILTER_INVALID"));
        mockMvc.perform(get("/api/v1/catalog/items").param("lat", "10.8").param("lng", "106.7")
                        .param("sort", "RELEVANCE"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("CATALOG_FILTER_INVALID"));
    }
}
