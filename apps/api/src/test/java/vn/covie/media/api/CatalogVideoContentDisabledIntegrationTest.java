package vn.covie.media.api;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import vn.covie.auth.application.InitialSessionIssuer;
import vn.covie.auth.model.DeviceMetadata;
import vn.covie.media.model.*;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.UserEntity;
import vn.covie.users.persistence.UserRepository;

@AutoConfigureMockMvc
@SpringBootTest(properties = {"covie.outbox.max-events-per-poll=0", "covie.retention.enabled=false"})
class CatalogVideoContentDisabledIntegrationTest extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired InitialSessionIssuer issuer;
    @Autowired UserRepository users;
    @Autowired MediaAssetRepository assets;
    @MockitoBean ObjectStorage storage;

    @Test void defaultOffHidesReadyVideoEvenFromOwnerWithoutOpeningOtherMediaRoutes() throws Exception {
        UUID owner = UUID.randomUUID();
        users.saveAndFlush(UserEntity.active(owner, "Video OFF owner", Instant.now()));
        String token = "Bearer " + issuer.issue(owner, new DeviceMetadata("Test", "Video OFF")).accessToken().value();
        var asset = MediaAssetEntity.pending(UUID.randomUUID(), owner, "private/off-video", MediaPurpose.CATALOG_VIDEO,
                "video/mp4", 10, Instant.now().plusSeconds(600), Instant.now());
        asset.recordVideoMetadata(64, 48, 1000, "h264");
        asset.markReady(Instant.now());
        assets.saveAndFlush(asset);
        String route = "/api/v1/media/" + asset.getId() + "/content";
        mvc.perform(get(route)).andExpect(status().isNotFound());
        mvc.perform(head(route)).andExpect(status().isNotFound());
        mvc.perform(get(route).header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/media/uploads").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType("application/json")
                .content("{\"purpose\":\"CATALOG_VIDEO\",\"contentType\":\"video/mp4\",\"sizeBytes\":10}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/media/" + asset.getId() + "/complete")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(storage);
    }
}
