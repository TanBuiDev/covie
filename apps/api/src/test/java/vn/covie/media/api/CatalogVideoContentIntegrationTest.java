package vn.covie.media.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.catalog.model.*;
import vn.covie.catalog.persistence.*;
import vn.covie.media.model.*;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.merchants.model.*;
import vn.covie.merchants.persistence.*;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
@SpringBootTest(properties = {"covie.social-v2.video-enabled=true", "covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false"})
class CatalogVideoContentIntegrationTest extends PostgresIntegrationTest {
    private static final byte[] BYTES = "0123456789".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired InitialSessionIssuer issuer;
    @Autowired UserRepository users;
    @Autowired MediaAssetRepository assets;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired io.micrometer.core.instrument.MeterRegistry metrics;
    @MockitoBean ObjectStorage storage;
    private UUID owner;
    private String token;
    private MediaAssetEntity asset;
    private String route;

    @BeforeEach void fixture() throws Exception {
        owner = user();
        token = bearer(owner);
        asset = MediaAssetEntity.pending(UUID.randomUUID(), owner, "private/video/" + UUID.randomUUID(),
                MediaPurpose.CATALOG_VIDEO, "video/mp4", BYTES.length, Instant.now().plusSeconds(600), Instant.now());
        asset.recordVideoMetadata(64, 48, 1000, "h264");
        asset.markReady(Instant.now());
        assets.saveAndFlush(asset);
        route = "/api/v1/media/" + asset.getId() + "/content";
        when(storage.openRange(anyString(), anyLong(), anyLong(), anyLong())).thenAnswer(invocation -> {
            assertThat(invocation.getArgument(0, String.class)).isEqualTo(asset.getStorageKey());
            assertThat(invocation.getArgument(3, Long.class)).isEqualTo((long) BYTES.length);
            int first = Math.toIntExact(invocation.getArgument(1, Long.class));
            int last = Math.toIntExact(invocation.getArgument(2, Long.class));
            return new ByteArrayInputStream(java.util.Arrays.copyOfRange(BYTES, first, last + 1));
        });
    }

    @Test void ownerCanReadFullSingleAndSuffixRangesButHeadNeverOpensStorage() throws Exception {
        mvc.perform(get(route).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(content().bytes(BYTES)).andExpect(content().contentType("video/mp4"))
                .andExpect(header().string("Content-Length", "10"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("Accept-Ranges", "bytes"));
        mvc.perform(get(route).header("Authorization", token).header("Range", "bytes=2-5"))
                .andExpect(status().isPartialContent()).andExpect(content().string("2345"))
                .andExpect(header().string("Content-Range", "bytes 2-5/10"))
                .andExpect(header().string("Content-Length", "4"));
        mvc.perform(get(route).header("Authorization", token).header("Range", "bytes=-3"))
                .andExpect(status().isPartialContent()).andExpect(content().string("789"));
        mvc.perform(get(route).header("Authorization", token).header("Range", "bytes=8-"))
                .andExpect(status().isPartialContent()).andExpect(content().string("89"));
        clearInvocations(storage);
        mvc.perform(head(route).header("Authorization", token)).andExpect(status().isOk())
                .andExpect(header().string("Content-Length", "10")).andExpect(content().bytes(new byte[0]));
        mvc.perform(get(route).header("Authorization", token).header("Range", "bytes=10-"))
                .andExpect(status().isRequestedRangeNotSatisfiable()).andExpect(header().string("Content-Range", "bytes */10"));
        mvc.perform(get(route).header("Authorization", token).header("Range", "bytes=0-1,4-5"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
        verifyNoInteractions(storage);
    }

    @Test void publicRequestsRecheckItemAndMerchantEligibilityAndPrivateReadersUseExistingMembership() throws Exception {
        mvc.perform(get(route)).andExpect(status().isNotFound()).andExpect(header().string("Cache-Control", "no-store"));
        String foreign = bearer(user());
        mvc.perform(get(route).header("Authorization", foreign)).andExpect(status().isNotFound());
        UUID merchantId = UUID.randomUUID();
        merchants.saveAndFlush(MerchantProfileEntity.create(merchantId, "Video content", "content-" + merchantId, Instant.now()));
        UUID category = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Video", "video-" + merchantId,
                CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        var item = CatalogItemEntity.create(UUID.randomUUID(), merchantId, CatalogItemType.PRODUCT, ListingKind.STANDARD,
                category, "Video item", "video-" + UUID.randomUUID(), null, Instant.now());
        item.publish(Instant.now());
        items.saveAndFlush(item);
        jdbc.update("insert into catalog_item_media(catalog_item_id,media_asset_id,media_type,sort_order,is_primary) values (?,?,'VIDEO',1,false)", item.getId(), asset.getId());
        var before = jdbc.queryForList("select to_jsonb(i)::text as row from catalog_items i where id=?", item.getId());
        mvc.perform(get(route).header("Range", "bytes=0-1")).andExpect(status().isPartialContent()).andExpect(content().string("01"));
        mvc.perform(head(route)).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        assertThat(jdbc.queryForList("select to_jsonb(i)::text as row from catalog_items i where id=?", item.getId())).isEqualTo(before);
        jdbc.update("update catalog_items set moderation_status='HIDDEN' where id=?", item.getId());
        clearInvocations(storage);
        mvc.perform(get(route).header("Range", "bytes=2-3")).andExpect(status().isNotFound());
        mvc.perform(head(route)).andExpect(status().isNotFound());
        verifyNoInteractions(storage);
        UUID reader = user();
        var membership = memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchantId, reader, MerchantRole.MANAGER, Instant.now()));
        String readerToken = bearer(reader);
        mvc.perform(get(route).header("Authorization", readerToken)).andExpect(status().isOk());
        jdbc.update("update merchant_memberships set membership_status='REVOKED' where id=?", membership.getId());
        mvc.perform(get(route).header("Authorization", readerToken)).andExpect(status().isNotFound());
        jdbc.update("update catalog_items set moderation_status='APPROVED' where id=?", item.getId());
        jdbc.update("update merchant_profiles set merchant_status='SUSPENDED' where id=?", merchantId);
        mvc.perform(get(route)).andExpect(status().isNotFound());
        mvc.perform(get(route).header("Authorization", token)).andExpect(status().isOk());
    }

    @Test void notReadyDeletedAndOtherPurposesNeverReachStorage() throws Exception {
        UUID admin = user();
        jdbc.update("insert into system_role_assignments(id,user_id,role,created_at) values (?,?,'ADMIN',now())", UUID.randomUUID(), admin);
        mvc.perform(get(route).header("Authorization", bearer(admin))).andExpect(status().isOk());
        clearInvocations(storage);
        jdbc.update("update media_assets set media_status='PENDING_UPLOAD' where id=?", asset.getId());
        mvc.perform(get(route).header("Authorization", token)).andExpect(status().isNotFound());
        jdbc.update("update media_assets set media_status='READY',deleted_at=now() where id=?", asset.getId());
        mvc.perform(get(route).header("Authorization", token)).andExpect(status().isNotFound());
        jdbc.update("update media_assets set deleted_at=null,upload_purpose='AD_BANNER' where id=?", asset.getId());
        mvc.perform(get(route).header("Authorization", token)).andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }

    @Test void transientAndTruncatedStorageNeverReturnSuccessfulContent() throws Exception {
        var prior = metrics.find("covie.catalog.video.delivery.failures").counter();
        double failures = prior == null ? 0 : prior.count();
        org.mockito.Mockito.doThrow(new IllegalStateException("Storage unavailable")).when(storage)
                .openRange(anyString(), anyLong(), anyLong(), anyLong());
        mvc.perform(get(route).header("Authorization", token)).andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"));
        org.mockito.Mockito.doReturn(new ByteArrayInputStream(new byte[]{0, 1})).when(storage)
                .openRange(anyString(), anyLong(), anyLong(), anyLong());
        mvc.perform(get(route).header("Authorization", token).header("Range", "bytes=0-3"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().doesNotExist("Content-Range"));
        assertThat(metrics.find("covie.catalog.video.delivery.failures").counter()).isNotNull();
        assertThat(metrics.get("covie.catalog.video.delivery.failures").counter().count()).isEqualTo(failures + 2);
    }

    @Test void countsInterruptedBodyAfterPartialResponseCommit() throws Exception {
        jdbc.update("update media_assets set size_bytes=10000 where id=?", asset.getId());
        double failures = metrics.get("covie.catalog.video.delivery.failures").counter().count();
        var interrupted = new java.io.InputStream() {
            private boolean delivered;
            @Override public int read() throws java.io.IOException { throw new java.io.IOException("Fixture interruption"); }
            @Override public int read(byte[] buffer, int offset, int length) throws java.io.IOException {
                if (delivered) throw new java.io.IOException("Fixture interruption");
                delivered = true;
                int count = Math.min(8192, length);
                java.util.Arrays.fill(buffer, offset, offset + count, (byte) 'x');
                return count;
            }
        };
        org.mockito.Mockito.doReturn(interrupted).when(storage).openRange(anyString(), anyLong(), anyLong(), anyLong());
        var result = mvc.perform(get(route).header("Authorization", token).header("Range", "bytes=0-9999")).andReturn();
        assertThat(result.getResolvedException()).isInstanceOf(java.io.IOException.class);
        assertThat(result.getResponse().isCommitted()).isTrue();
        assertThat(result.getResponse().getContentAsByteArray().length).isGreaterThanOrEqualTo(8192);
        var meter = metrics.get("covie.catalog.video.delivery.failures").counter();
        assertThat(meter.count()).isEqualTo(failures + 1);
        assertThat(meter.getId().getTags()).isEmpty();
    }

    private UUID user() {
        UUID id = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(id, "Content viewer", Instant.now()));
        return id;
    }

    private String bearer(UUID id) {
        return "Bearer " + issuer.issue(id, new DeviceMetadata("Test", "Video content")).accessToken().value();
    }
}
