package vn.covie.catalog.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import vn.covie.catalog.api.dto.edit.UpdateCatalogItemRequest;

class CatalogCaptionRequestTest {
    private final ObjectMapper json=new ObjectMapper();

    @Test void captionPresenceDistinguishesOmissionNullEmptyAndTextWithoutChangingOldPatchFields() throws Exception {
        var absent=json.readValue("{\"description\":\"Original description\"}",UpdateCatalogItemRequest.class);
        assertThat(absent.socialCaptionWasSupplied()).isFalse();
        assertThat(absent.description()).isEqualTo("Original description");
        for (String value : new String[]{"null","\"\"","\"Caption\\nsecond line\""}) {
            var request=json.readValue("{\"socialCaption\":"+value+"}",UpdateCatalogItemRequest.class);
            assertThat(request.socialCaptionWasSupplied()).isTrue();
            assertThat(request.description()).isNull();
            assertThat(request.mediaIds()).isNull();
        }
        var text=json.readValue("{\"socialCaption\":\"Caption\\nsecond line\",\"socialCaptionPresent\":false}",UpdateCatalogItemRequest.class);
        assertThat(text.socialCaption()).isEqualTo("Caption\nsecond line");
        assertThat(text.socialCaptionWasSupplied()).isTrue();
    }

    @Test void omittedOrNullCreateCaptionPreservesTheLegacyIdempotencyHash() throws Exception {
        String body="{\"type\":\"PRODUCT\",\"listingKind\":\"STANDARD\",\"categoryId\":\""+java.util.UUID.randomUUID()
                +"\",\"name\":\"Coffee\",\"locationOffers\":[]}";
        var request=json.readValue(body,vn.covie.catalog.api.dto.create.CreateCatalogItemRequest.class);
        var legacy=new java.util.LinkedHashMap<String,Object>();
        legacy.put("type",request.type()); legacy.put("listingKind",request.listingKind()); legacy.put("categoryId",request.categoryId());
        legacy.put("name",request.name()); legacy.put("description",request.description()); legacy.put("attributes",request.attributes());
        legacy.put("tags",request.tags()); legacy.put("mediaIds",request.mediaIds()); legacy.put("options",request.options());
        legacy.put("variants",request.variants()); legacy.put("locationOffers",request.locationOffers());
        var hashes=new vn.covie.shared.idempotency.IdempotencyHasher(json);
        assertThat(hashes.sha256(request)).isEqualTo(hashes.sha256(legacy));
        var explicitNull=json.readValue("{\"socialCaption\":null,"+body.substring(1),vn.covie.catalog.api.dto.create.CreateCatalogItemRequest.class);
        assertThat(hashes.sha256(explicitNull)).isEqualTo(hashes.sha256(legacy));
        var caption=json.readValue("{\"socialCaption\":\"Caption\","+body.substring(1),vn.covie.catalog.api.dto.create.CreateCatalogItemRequest.class);
        assertThat(hashes.sha256(caption)).isNotEqualTo(hashes.sha256(legacy));
    }

    @Test void captionValidationCountsUtf16UnitsAndAllowsNullEmptyNewlinesAndPlainHtmlText() throws Exception {
        try (var factory=Validation.buildDefaultValidatorFactory()) {
            var validator=factory.getValidator();
            for (String value : new String[]{null,"","Caption\nsecond line","<script>plain text</script>","a".repeat(3000),"😀".repeat(1500)}) {
                var request=json.readValue(json.writeValueAsString(java.util.Collections.singletonMap("socialCaption",value)),UpdateCatalogItemRequest.class);
                assertThat(validator.validate(request)).isEmpty();
            }
            for (String value : new String[]{"a".repeat(3001),"😀".repeat(1500)+"a"}) {
                var request=json.readValue(json.writeValueAsString(java.util.Map.of("socialCaption",value)),UpdateCatalogItemRequest.class);
                assertThat(validator.validate(request)).singleElement().satisfies(violation ->
                        assertThat(violation.getPropertyPath().toString()).isEqualTo("socialCaption"));
            }
        }
    }
}
