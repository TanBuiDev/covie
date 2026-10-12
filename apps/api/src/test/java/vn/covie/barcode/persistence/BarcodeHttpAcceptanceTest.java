package vn.covie.barcode.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
class BarcodeHttpAcceptanceTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @Test void publicLookupNormalizesGtinAndKeepsReferenceProjectionReadOnly() throws Exception {
        jdbc.update("insert into barcode_reference_products(gtin,product_name,market_signal,source,source_record_url,evidence_url,license_id,quality_status) "
                + "values ('8934673501354','Reference product','OFF_MARKET_TAG','OPEN_FOOD_FACTS','https://example.test/source','https://example.test/evidence','ODbL-1.0','COMMUNITY_UNVERIFIED')");
        for (String gtin : new String[] {"8934673501354", "08934673501354"}) {
            var result = mvc.perform(get("/api/v1/barcodes/" + gtin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.gtin14").value("08934673501354"))
                    .andExpect(jsonPath("$.productName").value("Reference product"))
                    .andExpect(jsonPath("$.needsReview").value(true))
                    .andExpect(jsonPath("$.licenseId").value("ODbL-1.0"))
                    .andExpect(jsonPath("$.source").value("OPEN_FOOD_FACTS"))
                    .andExpect(jsonPath("$.id").doesNotExist())
                    .andExpect(jsonPath("$.evidenceUrl").doesNotExist()).andReturn();
            assertThat(result.getResponse().getContentAsString()).contains("attributionNotice");
        }
        mvc.perform(get("/api/v1/barcodes/8934673501355")).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/barcodes/4006381333931")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        jdbc.update("update barcode_reference_products set is_active=false where gtin='8934673501354'");
        mvc.perform(get("/api/v1/barcodes/8934673501354")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
        assertThat(jdbc.queryForObject("select count(*) from barcode_reference_products where gtin='8934673501354'", Long.class)).isEqualTo(1);
    }
}
