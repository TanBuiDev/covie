package vn.covie.social.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.catalog.model.*;
import vn.covie.catalog.persistence.*;
import vn.covie.media.model.*;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.support.PostgresIntegrationTest;

@AutoConfigureMockMvc
@SpringBootTest(properties={"covie.social-v2.previews-enabled=true","covie.outbox.max-events-per-poll=0"})
class CatalogSocialPreviewIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MerchantProfileRepository merchants;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired CatalogItemMediaRepository mediaLinks;
    @Autowired MediaAssetRepository assets;
    private static final String ROUTE="/api/v1/catalog/social-previews";

    private CatalogItemEntity item(String state) {
        Instant now=Instant.now(); UUID merchantId=UUID.randomUUID();
        var merchant=MerchantProfileEntity.create(merchantId,"Preview merchant","preview-"+merchantId,now);
        merchants.saveAndFlush(merchant);
        UUID categoryId=categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(),null,"Preview category",
                "preview-category-"+UUID.randomUUID(),CategoryCatalogType.PRODUCT,CategoryStatus.ACTIVE,0)).getId();
        var item=CatalogItemEntity.create(UUID.randomUUID(),merchantId,CatalogItemType.PRODUCT,ListingKind.STANDARD,
                categoryId,"Preview item","preview-item-"+UUID.randomUUID(),"Commercial description",now);
        if (!state.equals("DRAFT")) item.publish(now);
        switch (state) {
            case "PAUSED" -> item.pause(now);
            case "HIDDEN" -> item.hide(now);
            case "PENDING" -> item.requireModeration(now);
            case "REJECTED" -> { item.requireModeration(now); item.rejectModeration(now); }
            case "DELETED" -> item.delete(now);
            case "MERCHANT_SUSPENDED" -> { merchant.suspend(now); merchants.saveAndFlush(merchant); }
            case "MERCHANT_HIDDEN" -> { merchant.hide(now); merchants.saveAndFlush(merchant); }
            default -> { }
        }
        item.updateSocialCaption("Caption\n<script>plain text</script>",now);
        return items.saveAndFlush(item);
    }

    @Test void previewsOnlyVisibleItemsAndSixOrderedReadyImagesWithoutChangingRows() throws Exception {
        var visible=item("VISIBLE"); var empty=item("VISIBLE");
        empty.updateSocialCaption(null,Instant.now()); items.saveAndFlush(empty);
        var requested=new ArrayList<String>(); requested.add(empty.getId().toString()); requested.add(visible.getId().toString());
        requested.add(visible.getId().toString());
        for (String state : List.of("DRAFT","PAUSED","HIDDEN","PENDING","REJECTED","DELETED","MERCHANT_SUSPENDED","MERCHANT_HIDDEN"))
            requested.add(item(state).getId().toString());
        var merchantDeleted=item("VISIBLE");
        jdbc.update("update merchant_profiles set deleted_at=now() where id=?",merchantDeleted.getMerchantProfileId()); requested.add(merchantDeleted.getId().toString());
        var merchantPending=item("VISIBLE");
        jdbc.update("update merchant_profiles set moderation_status='PENDING' where id=?",merchantPending.getMerchantProfileId()); requested.add(merchantPending.getId().toString());
        requested.add(UUID.randomUUID().toString());
        var readyIds=new ArrayList<UUID>();
        for (int index=0;index<11;index++) {
            var asset=MediaAssetEntity.pending(UUID.randomUUID(),null,"preview-asset-"+UUID.randomUUID(),
                    index==10?MediaPurpose.AD_BANNER:MediaPurpose.CATALOG_IMAGE,"image/jpeg",128,Instant.now().plusSeconds(600),Instant.now());
            if (index>=1 && index<=7 || index>=9) asset.markReady(Instant.now());
            if (index==8) asset.markFailed();
            assets.saveAndFlush(asset);
            if (index==9) jdbc.update("update media_assets set deleted_at=now() where id=?",asset.getId());
            mediaLinks.saveAndFlush(CatalogItemMediaEntity.image(visible,asset,index,index==0));
            if (index>=1 && index<=6) readyIds.add(asset.getId());
        }
        var beforeItems=jdbc.queryForList("select * from catalog_items order by id");
        var beforeMedia=jdbc.queryForList("select * from media_assets order by id");
        var response=mvc.perform(get(ROUTE).param("itemIds",requested.toArray(String[]::new))).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control","no-store"))
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$['"+visible.getId()+"'].socialCaption").value("Caption\n<script>plain text</script>"))
                .andExpect(jsonPath("$['"+visible.getId()+"'].media.length()").value(6))
                .andExpect(jsonPath("$['"+empty.getId()+"'].media").isEmpty())
                .andExpect(jsonPath("$['"+empty.getId()+"'].socialCaption").doesNotExist()).andReturn();
        var body=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString());
        var returned=new ArrayList<String>(); body.fieldNames().forEachRemaining(returned::add);
        assertThat(returned).containsExactly(empty.getId().toString(),visible.getId().toString());
        for (int index=0;index<6;index++) {
            var media=body.path(visible.getId().toString()).path("media").get(index);
            assertThat(media.path("id").asText()).isEqualTo(readyIds.get(index).toString());
            assertThat(media.path("sortOrder").asInt()).isEqualTo(index+1);
            assertThat(media.path("isCover").asBoolean()).isEqualTo(index==0);
            assertThat(media.path("url").asText()).startsWith("http");
            assertThat(media.has("storageKey")).isFalse();
        }
        assertThat(body.path(visible.getId().toString()).has("liked")).isFalse();
        assertThat(body.path(visible.getId().toString()).has("description")).isFalse();
        assertThat(jdbc.queryForList("select * from catalog_items order by id")).isEqualTo(beforeItems);
        assertThat(jdbc.queryForList("select * from media_assets order by id")).isEqualTo(beforeMedia);
    }

    @Test void validatesRawBatchBoundsAndUuidWhileDeduplicatingValidIds() throws Exception {
        var item=item("VISIBLE"); String id=item.getId().toString();
        mvc.perform(get(ROUTE)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(ROUTE).param("itemIds","")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(ROUTE).param("itemIds","not-a-uuid")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(ROUTE).param("itemIds","1-1-1-1-1")).andExpect(status().isUnprocessableEntity());
        mvc.perform(get(ROUTE).param("itemIds",java.util.Collections.nCopies(40,id).toArray(String[]::new)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        var distinct=new ArrayList<String>(); distinct.add(id);
        for (int index=1;index<40;index++) distinct.add(UUID.randomUUID().toString());
        mvc.perform(get(ROUTE).param("itemIds",distinct.toArray(String[]::new)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get(ROUTE).param("itemIds",java.util.Collections.nCopies(41,id).toArray(String[]::new)))
                .andExpect(status().isUnprocessableEntity());
    }
}
