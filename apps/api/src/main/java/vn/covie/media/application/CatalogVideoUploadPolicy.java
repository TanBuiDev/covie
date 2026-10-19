package vn.covie.media.application;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

@Service
public class CatalogVideoUploadPolicy {
    private final ObjectStorage storage;
    private final MerchantMembershipRepository memberships;
    private final MerchantAccessPolicy access;
    private final CatalogVideoProbe probe;
    private final boolean enabled;
    private final Semaphore capacity;
    private final URI publicBaseUrl;
    private final java.time.Duration downloadTimeout;

    public CatalogVideoUploadPolicy(ObjectStorage storage, MerchantMembershipRepository memberships,
            MerchantAccessPolicy access, CatalogVideoProbe probe,
            @Value("${covie.social-v2.video-enabled:false}") boolean enabled,
            @Value("${covie.social-v2.video-probe-concurrency:2}") int concurrency,
            @Value("${covie.production.public-url:http://localhost:3000}") String publicBaseUrl,
            @Value("${covie.social-v2.video-download-timeout:PT20S}") String downloadTimeout) {
        if (concurrency < 1) throw new IllegalArgumentException("video-probe-concurrency must be positive");
        this.storage = storage;
        this.memberships = memberships;
        this.access = access;
        this.probe = probe;
        this.enabled = enabled;
        this.capacity = new Semaphore(concurrency);
        this.publicBaseUrl = URI.create(publicBaseUrl);
        this.downloadTimeout = java.time.Duration.parse(downloadTimeout);
        if (this.downloadTimeout.isNegative() || this.downloadTimeout.isZero()) throw new IllegalArgumentException("video-download-timeout must be positive");
        this.downloadTimeout.toNanos();
    }

    public URI contentUrl(java.util.UUID mediaId) {
        return publicBaseUrl.resolve("/api/v1/media/" + mediaId + "/content");
    }

    public void requireEnabled() {
        if (!enabled) throw new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }

    public boolean enabled() { return enabled; }

    public void requireUpload(CurrentActor actor) {
        requireEnabled();
        var merchant = memberships.findActiveMerchantSummaries(actor.userId()).stream()
                .filter(summary -> MerchantAccessPolicy.allows(summary.currentUserRole(), MerchantCapability.MANAGE_CATALOG_STRUCTURE))
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.MERCHANT_ACTION_NOT_ALLOWED, HttpStatus.FORBIDDEN,
                        "Catalog authoring permission is required."));
        access.requireForMutation(merchant.id(), MerchantCapability.MANAGE_CATALOG_STRUCTURE);
    }

    public Optional<CatalogVideoProbe.Metadata> verify(MediaAssetEntity asset) {
        requireEnabled();
        if (!capacity.tryAcquire()) throw new ApiException(ErrorCode.MEDIA_UPLOAD_LIMIT_REACHED, HttpStatus.TOO_MANY_REQUESTS,
                "Video processing capacity is reached. Retry later.");
        Path workspace = null;
        try {
            workspace = Files.createTempDirectory("covie-video-");
            Path input = workspace.resolve("input");
            storage.downloadTo(asset.getStorageKey(), input, asset.getSizeBytes(), downloadTimeout);
            return Optional.of(probe.inspect(input, asset.getMimeType()));
        } catch (CatalogVideoProbe.InvalidVideoException invalid) {
            return Optional.empty();
        } catch (IOException | RuntimeException unavailable) {
            throw unavailable();
        } finally {
            try {
                if (workspace != null) {
                    Files.deleteIfExists(workspace.resolve("input"));
                    Files.deleteIfExists(workspace);
                }
            } catch (IOException cleanupFailure) {
                throw unavailable();
            } finally {
                capacity.release();
            }
        }
    }

    static ApiException unavailable() {
        return new ApiException(ErrorCode.MEDIA_UPLOAD_VERIFICATION_FAILED, HttpStatus.SERVICE_UNAVAILABLE,
                "Video processing is unavailable. Retry later.");
    }
}
