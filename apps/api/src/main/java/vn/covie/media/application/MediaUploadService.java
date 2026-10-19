package vn.covie.media.application;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.media.api.dto.CreateMediaUploadRequest;
import vn.covie.media.api.dto.MediaResponse;
import vn.covie.media.api.dto.MediaUploadResponse;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.media.storage.ObjectStorage;
import vn.covie.media.storage.PresignedUpload;
import vn.covie.media.storage.StorageProperties;
import vn.covie.media.storage.StoredObjectMetadata;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.idempotency.IdempotencyExecutor;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Service
public class MediaUploadService {
    private static final String COMPLETE_OPERATION = "media.complete";
    private final CurrentActorProvider actors;
    private final MediaAssetRepository repository;
    private final ObjectStorage storage;
    private final StorageProperties properties;
    private final MediaMagicSniffer magic;
    private final MediaUrlResolver urls;
    private final IdempotencyExecutor idempotency;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;
    private final vn.covie.users.persistence.UserRepository users;
    private final CatalogVideoUploadPolicy videos;

    public MediaUploadService(CurrentActorProvider actors, MediaAssetRepository repository, ObjectStorage storage,
            StorageProperties properties, MediaMagicSniffer magic, MediaUrlResolver urls, IdempotencyExecutor idempotency,
            UuidV7Generator ids, AppClock clock, AuditLogWriter audit, RequestIdAccessor requestIds,
            vn.covie.users.persistence.UserRepository users, CatalogVideoUploadPolicy videos) {
        this.actors = actors; this.repository = repository; this.storage = storage; this.properties = properties;
        this.magic = magic; this.urls = urls; this.idempotency = idempotency; this.ids = ids; this.clock = clock;
        this.audit = audit; this.requestIds = requestIds;
        this.users = users;
        this.videos = videos;
    }

    @org.springframework.transaction.annotation.Transactional
    public MediaUploadResponse createUpload(CreateMediaUploadRequest request) {
        CurrentActor actor = actors.currentActor();
        boolean catalogVideo = request.purpose() == vn.covie.media.model.MediaPurpose.CATALOG_VIDEO;
        if (catalogVideo) videos.requireEnabled();
        String contentType = normalizeSupportedType(request.contentType(), request.purpose());
        long maxBytes = contentType.startsWith("video/") ? properties.maxVideoBytes() : properties.maxImageBytes();
        if (request.fileSize() <= 0 || request.fileSize() > maxBytes) throw new ApiException(ErrorCode.MEDIA_UPLOAD_VERIFICATION_FAILED,
                HttpStatus.UNPROCESSABLE_ENTITY, "Media exceeds the configured size limit.");
        users.findByIdForUpdate(actor.userId()).orElseThrow(() -> new ApiException(
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
        if (catalogVideo) videos.requireUpload(actor);
        if (repository.countByOwnerUserIdAndMediaStatus(actor.userId(), MediaStatus.PENDING_UPLOAD)
                >= properties.maxPendingUploadsPerUser()) throw new ApiException(ErrorCode.MEDIA_UPLOAD_LIMIT_REACHED,
                HttpStatus.TOO_MANY_REQUESTS, "Media upload limit reached.");
        UUID mediaId = ids.generate();
        Instant now = clock.now();
        String key = "users/%s/%s.%s".formatted(actor.userId(), mediaId, extension(contentType));
        PresignedUpload signed = storage.presignPut(key, contentType, request.fileSize(), properties.uploadUrlTtl());
        repository.saveAndFlush(MediaAssetEntity.pending(mediaId, actor.userId(), key, request.purpose(), contentType,
                request.fileSize(), signed.expiresAt(), now));
        return new MediaUploadResponse(mediaId, signed.method(), signed.url(), signed.requiredHeaders(), signed.expiresAt());
    }

    public MediaResponse complete(UUID mediaId, String idempotencyKey) {
        CurrentActor actor = actors.currentActor();
        requireIdempotencyKey(idempotencyKey);
        var execution = idempotency.executeWithStatus(actor.userId(), COMPLETE_OPERATION, idempotencyKey,
                Map.of("mediaId", mediaId), CompletionOutcome.class, () -> completeInTransaction(mediaId, actor));
        CompletionOutcome outcome = execution.response();
        if (outcome.failedStorageKey() != null && !execution.replayed()) {
            try { storage.deleteIfExists(outcome.failedStorageKey()); } catch (RuntimeException ignored) { }
        }
        if (outcome.response() == null) throw verificationFailed();
        if (outcome.response().purpose() == vn.covie.media.model.MediaPurpose.CATALOG_VIDEO) videos.requireEnabled();
        return outcome.response();
    }

    private IdempotencyExecutor.StoredResponse<CompletionOutcome> completeInTransaction(UUID mediaId, CurrentActor actor) {
        MediaAssetEntity asset = ownedForUpdate(mediaId, actor.userId());
        boolean catalogVideo = asset.getPurpose() == vn.covie.media.model.MediaPurpose.CATALOG_VIDEO;
        if (catalogVideo) videos.requireUpload(actor);
        if (asset.getMediaStatus() == MediaStatus.READY) {
            return new IdempotencyExecutor.StoredResponse<>(200, CompletionOutcome.ready(response(asset)));
        }
        if (asset.getMediaStatus() == MediaStatus.PENDING_UPLOAD) {
            if (objectMatches(asset)) {
                if (catalogVideo) {
                    var metadata = videos.verify(asset);
                    if (metadata.isEmpty()) {
                        boolean changed = asset.markFailed();
                        return new IdempotencyExecutor.StoredResponse<>(422, CompletionOutcome.failed(changed ? asset.getStorageKey() : null));
                    }
                    var verified = metadata.get();
                    asset.recordVideoMetadata(verified.width(), verified.height(), verified.durationMillis(), verified.codec());
                }
                asset.markReady(clock.now().truncatedTo(ChronoUnit.MICROS));
                audit.securityEvent(actor.userId(), "MEDIA_UPLOAD_COMPLETED", "MEDIA", asset.getId(), requestIds.current(), Map.of());
                return new IdempotencyExecutor.StoredResponse<>(200, CompletionOutcome.ready(response(asset)));
            }
            boolean changed = asset.markFailed();
            return new IdempotencyExecutor.StoredResponse<>(422, CompletionOutcome.failed(changed ? asset.getStorageKey() : null));
        }
        return new IdempotencyExecutor.StoredResponse<>(422, CompletionOutcome.failed(null));
    }

    private boolean objectMatches(MediaAssetEntity asset) {
        try {
            StoredObjectMetadata metadata = storage.head(asset.getStorageKey());
            return metadata.contentLength() == asset.getSizeBytes()
                    && normalizeStoredType(metadata.contentType()).equals(asset.getMimeType())
                    && magic.matches(asset.getMimeType(), storage.readPrefix(asset.getStorageKey(), asset.getMimeType().startsWith("video/") ? 4096 : 32));
        } catch (RuntimeException exception) {
            if (asset.getPurpose() == vn.covie.media.model.MediaPurpose.CATALOG_VIDEO) throw CatalogVideoUploadPolicy.unavailable();
            return false;
        }
    }

    private MediaAssetEntity owned(UUID mediaId, UUID userId) {
        return repository.findByIdAndOwnerUserId(mediaId, userId).orElseThrow(() -> new ApiException(
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
    }
    private MediaAssetEntity ownedForUpdate(UUID mediaId, UUID userId) {
        return repository.findByIdAndOwnerUserIdForUpdate(mediaId, userId).orElseThrow(() -> new ApiException(
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
    }
    private MediaResponse response(MediaAssetEntity asset) {
        if (asset.getPurpose() == vn.covie.media.model.MediaPurpose.CATALOG_VIDEO)
            return new MediaResponse(asset.getId(), asset.getPurpose(), asset.getMimeType(), asset.getSizeBytes(),
                    videos.contentUrl(asset.getId()), asset.getVerifiedAt());
        return new MediaResponse(asset.getId(), asset.getPurpose(), asset.getMimeType(), asset.getSizeBytes(),
                urls.publicUrl(asset), asset.getVerifiedAt());
    }
    private static ApiException verificationFailed() { return new ApiException(ErrorCode.MEDIA_UPLOAD_VERIFICATION_FAILED,
            HttpStatus.UNPROCESSABLE_ENTITY, "Media upload verification failed."); }
    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) throw new ApiException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED,
                HttpStatus.BAD_REQUEST, "Idempotency-Key is required.");
    }
    private static String normalizeSupportedType(String type, vn.covie.media.model.MediaPurpose purpose) {
        String normalized = normalizeStoredType(type);
        boolean catalogVideo = purpose == vn.covie.media.model.MediaPurpose.CATALOG_VIDEO;
        boolean video = (purpose == vn.covie.media.model.MediaPurpose.AD_BANNER || catalogVideo)
                && (normalized.equals("video/mp4") || normalized.equals("video/webm"));
        if (catalogVideo && !video) throw new ApiException(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED,
                HttpStatus.UNPROCESSABLE_ENTITY, "Catalog video must be MP4 or WebM.");
        if (!video && !normalized.equals("image/jpeg") && !normalized.equals("image/png") && !normalized.equals("image/webp"))
            throw new ApiException(ErrorCode.MEDIA_TYPE_NOT_SUPPORTED, HttpStatus.UNPROCESSABLE_ENTITY, "Media type is not supported.");
        return normalized;
    }
    private static String normalizeStoredType(String type) { return type == null ? "" : type.split(";", 2)[0].strip().toLowerCase(java.util.Locale.ROOT); }
    private static String extension(String contentType) { return switch (contentType) {
        case "image/jpeg" -> "jpg"; case "image/png" -> "png"; case "image/webp" -> "webp";
        case "video/mp4" -> "mp4"; case "video/webm" -> "webm"; default -> throw new IllegalArgumentException();
    }; }
    public record CompletionOutcome(MediaResponse response, String failedStorageKey) {
        static CompletionOutcome ready(MediaResponse response) { return new CompletionOutcome(response, null); }
        static CompletionOutcome failed(String failedStorageKey) { return new CompletionOutcome(null, failedStorageKey); }
    }
}
