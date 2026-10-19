package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
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
import vn.covie.shared.web.ApiException;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
@SpringBootTest(properties = {"covie.social-v2.video-enabled=true", "covie.retention.enabled=false", "covie.outbox.max-events-per-poll=0"})
class CatalogVideoCleanupIntegrationTest extends PostgresIntegrationTest {
    @Autowired CatalogVideoCleanupService cleanup;
    @Autowired MediaAssetRepository assets;
    @Autowired UserRepository users;
    @Autowired MerchantProfileRepository merchants;
    @Autowired MerchantMembershipRepository memberships;
    @Autowired CategoryRepository categories;
    @Autowired CatalogItemRepository items;
    @Autowired CatalogItemMediaRepository links;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired MockMvc mvc;
    @Autowired InitialSessionIssuer issuer;
    @MockitoBean ObjectStorage storage;

    @Test void boundsCleanupAndLeavesAttachedRecentLivePresignAndImageRowsUnchanged() {
        Fixture fixture = fixture();
        var pending = asset(fixture.owner(), false);
        var failed = asset(fixture.owner(), false); failed.markFailed(); assets.saveAndFlush(failed);
        var orphan = asset(fixture.owner(), true);
        var attached = asset(fixture.owner(), true);
        links.saveAndFlush(CatalogItemMediaEntity.video(items.findById(fixture.item()).orElseThrow(), attached, 0));
        var livePresign = asset(fixture.owner(), true);
        jdbc.update("update media_assets set upload_expires_at=now()+interval '1 hour' where id=?", livePresign.getId());
        var recent = asset(fixture.owner(), true);
        jdbc.update("update media_assets set verified_at=now() where id=?", recent.getId());
        var image = MediaAssetEntity.pending(UUID.randomUUID(), fixture.owner(), "old/image/" + UUID.randomUUID(), MediaPurpose.CATALOG_IMAGE,
                "image/png", 24, Instant.now().minus(Duration.ofDays(30)), Instant.now().minus(Duration.ofDays(30)));
        image.markReady(Instant.now().minus(Duration.ofDays(30))); assets.saveAndFlush(image);
        List<UUID> protectedIds = List.of(attached.getId(), livePresign.getId(), recent.getId(), image.getId());
        var before = protectedIds.stream().map(this::row).toList();
        assertThat(cleanup.cleanup(2)).isEqualTo(2);
        assertThat(cleanup.cleanup(10)).isEqualTo(1);
        assertThat(cleanup.cleanup(10)).isZero();
        assertThat(protectedIds.stream().map(this::row).toList()).isEqualTo(before);
        for (var target : List.of(pending, failed, orphan)) {
            assertThat(assets.findById(target.getId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.DELETED);
            assertThat(jdbc.queryForObject("select storage_deleted_at is not null from media_assets where id=?", Boolean.class, target.getId())).isTrue();
            verify(storage).deleteIfExists(target.getStorageKey(), Duration.ofSeconds(20));
        }
        verify(storage, times(3)).deleteIfExists(anyString(), any(Duration.class));
    }

    @Test void failedDeletePersistsRetryLeaseAndSuccessfulRetryIsNotRepeated() {
        var fixture = fixture();
        var target = asset(fixture.owner(), true);
        doThrow(new IllegalStateException("Unavailable storage")).doNothing().when(storage).deleteIfExists(target.getStorageKey(), Duration.ofSeconds(20));
        assertThatThrownBy(() -> cleanup.cleanup(1)).isInstanceOf(ApiException.class);
        assertThat(assets.findById(target.getId()).orElseThrow().getMediaStatus()).isEqualTo(MediaStatus.DELETED);
        assertThat(jdbc.queryForObject("select storage_deleted_at is null and storage_cleanup_attempted_at is not null from media_assets where id=?", Boolean.class, target.getId())).isTrue();
        assertThat(cleanup.cleanup(1)).isZero();
        verify(storage, times(1)).deleteIfExists(target.getStorageKey(), Duration.ofSeconds(20));
        jdbc.update("update media_assets set storage_cleanup_attempted_at=now()-interval '1 day' where id=?", target.getId());
        assertThat(cleanup.cleanup(1)).isEqualTo(1);
        assertThat(cleanup.cleanup(1)).isZero();
        verify(storage, times(2)).deleteIfExists(target.getStorageKey(), Duration.ofSeconds(20));
    }

    @Test void actualSellerPatchAndCleanupAreSafeInBothOrders() throws Exception {
        var sellerFirst = fixture();
        var firstAsset = asset(sellerFirst.owner(), true);
        try (var workers = Executors.newVirtualThreadPerTaskExecutor(); Connection blocker = dataSource.getConnection()) {
            blocker.setAutoCommit(false);
            try (var statement = blocker.prepareStatement("select id from catalog_items where id=? for no key update")) {
                statement.setObject(1, sellerFirst.item()); statement.executeQuery().close();
            }
            var patching = workers.submit(() -> attach(sellerFirst, firstAsset.getId()));
            try {
                long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
                boolean waiting = false;
                while (System.nanoTime() < deadline && !patching.isDone()) {
                    waiting = jdbc.queryForObject("select count(*) from pg_stat_activity where wait_event_type='Lock' and lower(query) like 'update catalog_items set%'", Integer.class) > 0;
                    if (waiting) break;
                    Thread.sleep(25);
                }
                assertThat(waiting).as("Seller reached item UPDATE while holding the video attachment lock").isTrue();
                assertThat(cleanup.cleanup(1)).isZero();
                verifyNoInteractions(storage);
            } finally { blocker.commit(); }
            assertThat(patching.get(8, TimeUnit.SECONDS)).isEqualTo(204);
            assertThat(links.existsByIdCatalogItemIdAndIdMediaAssetId(sellerFirst.item(), firstAsset.getId())).isTrue();
            assertThat(cleanup.cleanup(1)).isZero();
        }
        var cleanupFirst = fixture();
        var secondAsset = asset(cleanupFirst.owner(), true);
        var enteredDelete = new CountDownLatch(1);
        var releaseDelete = new CountDownLatch(1);
        doAnswer(invocation -> { enteredDelete.countDown(); if (!releaseDelete.await(8, TimeUnit.SECONDS)) throw new IllegalStateException("Fixture timeout"); return null; })
                .when(storage).deleteIfExists(secondAsset.getStorageKey(), Duration.ofSeconds(20));
        var beforeItem = jdbc.queryForObject("select to_jsonb(i)::text from catalog_items i where id=?", String.class, cleanupFirst.item());
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            var deleting = workers.submit(() -> cleanup.cleanup(1));
            try {
                assertThat(enteredDelete.await(8, TimeUnit.SECONDS)).isTrue();
                assertThat(cleanup.cleanup(1)).isZero();
                assertThat(attach(cleanupFirst, secondAsset.getId())).isEqualTo(422);
                assertThat(links.existsByIdCatalogItemIdAndIdMediaAssetId(cleanupFirst.item(), secondAsset.getId())).isFalse();
                assertThat(jdbc.queryForObject("select to_jsonb(i)::text from catalog_items i where id=?", String.class, cleanupFirst.item())).isEqualTo(beforeItem);
            } finally { releaseDelete.countDown(); }
            assertThat(deleting.get(8, TimeUnit.SECONDS)).isEqualTo(1);
        }
    }

    private int attach(Fixture fixture, UUID media) throws Exception {
        return mvc.perform(patch("/api/v1/merchants/" + fixture.merchant() + "/catalog-items/" + fixture.item())
                .header("Authorization", fixture.token()).header("If-Match", "\"v0\"").contentType("application/json")
                .content("{\"mediaIds\":[\"" + media + "\"]}")).andReturn().getResponse().getStatus();
    }

    private MediaAssetEntity asset(UUID owner, boolean ready) {
        Instant old = Instant.now().minus(Duration.ofDays(30));
        var asset = MediaAssetEntity.pending(UUID.randomUUID(), owner, "cleanup/" + UUID.randomUUID(), MediaPurpose.CATALOG_VIDEO, "video/mp4", 24, old.plusSeconds(600), old);
        if (ready) { asset.recordVideoMetadata(64, 48, 1000, "h264"); asset.markReady(old); }
        return assets.saveAndFlush(asset);
    }

    private String row(UUID id) { return jdbc.queryForObject("select to_jsonb(a)::text from media_assets a where id=?", String.class, id); }

    private Fixture fixture() {
        UUID owner = UUID.randomUUID(), merchant = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner, "Video cleanup owner", Instant.now()));
        merchants.saveAndFlush(MerchantProfileEntity.create(merchant, "Cleanup shop", "cleanup-" + merchant, Instant.now()));
        memberships.saveAndFlush(MerchantMembershipEntity.active(UUID.randomUUID(), merchant, owner, MerchantRole.OWNER, Instant.now()));
        UUID category = categories.saveAndFlush(CategoryEntity.create(UUID.randomUUID(), null, "Cleanup", "cleanup-" + merchant, CategoryCatalogType.PRODUCT, CategoryStatus.ACTIVE, 0)).getId();
        UUID item = items.saveAndFlush(CatalogItemEntity.create(UUID.randomUUID(), merchant, CatalogItemType.PRODUCT, ListingKind.STANDARD, category,
                "Cleanup item", "cleanup-item-" + merchant, null, Instant.now())).getId();
        String token = "Bearer " + issuer.issue(owner, new DeviceMetadata("Test", "Video cleanup")).accessToken().value();
        return new Fixture(owner, merchant, item, token);
    }

    private record Fixture(UUID owner, UUID merchant, UUID item, String token) {}
}
