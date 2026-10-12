package vn.covie.merchants.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.audit.AuditLogWriter;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.media.application.MediaAttachmentPolicy;
import vn.covie.media.application.MediaUrlResolver;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.merchants.api.dto.CreateMerchantRequest;
import vn.covie.merchants.api.dto.MerchantManagementResponse;
import vn.covie.merchants.api.dto.MerchantSummaryResponse;
import vn.covie.merchants.api.dto.UpdateMerchantRequest;
import vn.covie.merchants.model.MerchantMembershipEntity;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantRole;
import vn.covie.merchants.persistence.MerchantMembershipRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.merchants.security.MerchantAccessPolicy;
import vn.covie.merchants.security.MerchantCapability;
import vn.covie.shared.concurrency.VersionPrecondition;
import vn.covie.shared.id.UuidV7Generator;
import vn.covie.shared.security.CurrentActor;
import vn.covie.shared.time.AppClock;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.shared.web.RequestIdAccessor;

@Service
public class MerchantApplicationService {
    private static final int MAX_SLUG_ID_ATTEMPTS = 3;
    private final CurrentActorProvider actors;
    private final MerchantProfileRepository merchants;
    private final MerchantMembershipRepository memberships;
    private final MerchantSlugService slugs;
    private final MerchantAccessPolicy access;
    private final MediaAttachmentPolicy media;
    private final UuidV7Generator ids;
    private final AppClock clock;
    private final AuditLogWriter audit;
    private final RequestIdAccessor requestIds;
    private final MediaAssetRepository mediaAssets;
    private final MediaUrlResolver urls;

    public MerchantApplicationService(CurrentActorProvider actors, MerchantProfileRepository merchants,
            MerchantMembershipRepository memberships, MerchantSlugService slugs, MerchantAccessPolicy access,
            MediaAttachmentPolicy media, UuidV7Generator ids, AppClock clock, AuditLogWriter audit,
            RequestIdAccessor requestIds) {
        this(actors, merchants, memberships, slugs, access, media, ids, clock, audit, requestIds, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public MerchantApplicationService(CurrentActorProvider actors, MerchantProfileRepository merchants,
            MerchantMembershipRepository memberships, MerchantSlugService slugs, MerchantAccessPolicy access,
            MediaAttachmentPolicy media, UuidV7Generator ids, AppClock clock, AuditLogWriter audit,
            RequestIdAccessor requestIds, MediaAssetRepository mediaAssets, MediaUrlResolver urls) {
        this.actors = actors;
        this.merchants = merchants;
        this.memberships = memberships;
        this.slugs = slugs;
        this.access = access;
        this.media = media;
        this.ids = ids;
        this.clock = clock;
        this.audit = audit;
        this.requestIds = requestIds;
        this.mediaAssets = mediaAssets;
        this.urls = urls;
    }

    @Transactional
    public MerchantSummaryResponse create(CreateMerchantRequest request) {
        CurrentActor actor = actors.currentActor();
        Instant now = clock.now();
        MerchantProfileEntity merchant = createProfile(request, now);
        UUID merchantId = merchant.getId();
        merchants.save(merchant);
        memberships.save(MerchantMembershipEntity.active(ids.generate(), merchantId, actor.userId(), MerchantRole.OWNER, now));
        audit.securityEvent(actor.userId(), "MERCHANT_CREATED", "MERCHANT", merchantId, requestIds.current(), Map.of());
        return summary(merchant, MerchantRole.OWNER);
    }

    private MerchantProfileEntity createProfile(CreateMerchantRequest request, Instant now) {
        for (int attempt = 0; attempt < MAX_SLUG_ID_ATTEMPTS; attempt++) {
            UUID merchantId = ids.generate();
            try {
                return MerchantProfileEntity.create(merchantId, requiredName(request.name()),
                        slugs.uniqueSlug(request.name(), merchantId), cleanNullable(request.description()), now);
            } catch (MerchantSlugService.SlugCollisionException ignored) {
                // A pre-existing suffix collided; retry with a fresh merchant ID while still holding the base-slug lock.
            }
        }
        throw new IllegalStateException("Unable to allocate a unique merchant slug.");
    }

    @Transactional(readOnly = true)
    public List<MerchantSummaryResponse> mine() {
        return memberships.findActiveMerchantSummaries(actors.currentActor().userId());
    }

    @Transactional(readOnly = true)
    public MerchantManagementResponse get(UUID merchantId) {
        access.require(merchantId, MerchantCapability.VIEW_MERCHANT);
        return management(profile(merchantId));
    }

    @Transactional
    public MerchantManagementResponse update(UUID merchantId, String ifMatch, UpdateMerchantRequest request) {
        access.require(merchantId, MerchantCapability.EDIT_PROFILE);
        MerchantProfileEntity merchant = profile(merchantId);
        VersionPrecondition.require(VersionPrecondition.parseRequired(ifMatch), merchant.getVersion());
        CurrentActor actor = actors.currentActor();
        UUID logoMediaId = request.logoMediaId() == null ? merchant.getLogoMediaId() : request.logoMediaId();
        UUID coverMediaId = request.coverMediaId() == null ? merchant.getCoverMediaId() : request.coverMediaId();
        if (request.logoMediaId() != null) media.requireMerchantLogo(request.logoMediaId(), actor.userId());
        if (request.coverMediaId() != null) media.requireMerchantCover(request.coverMediaId(), actor.userId());
        merchant.update(request.name() == null ? merchant.getName() : requiredName(request.name()),
                request.description() == null ? merchant.getDescription() : cleanNullable(request.description()),
                logoMediaId, coverMediaId, clock.now());
        return management(merchants.saveAndFlush(merchant));
    }

    private MerchantProfileEntity profile(UUID merchantId) {
        return merchants.findByIdAndDeletedAtIsNull(merchantId).orElseThrow(() -> new ApiException(
                ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found."));
    }

    private static String requiredName(String value) {
        String cleaned = value == null ? "" : value.strip();
        if (cleaned.isBlank()) throw new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY,
                "Merchant name must not be blank.");
        return cleaned;
    }

    private static String cleanNullable(String value) {
        if (value == null) return null;
        String cleaned = value.strip();
        return cleaned.isEmpty() ? null : cleaned;
    }

    private static MerchantSummaryResponse summary(MerchantProfileEntity merchant, MerchantRole role) {
        return new MerchantSummaryResponse(merchant.getId(), merchant.getName(), merchant.getSlug(),
                merchant.getMerchantStatus(), role, merchant.getVersion());
    }

    private MerchantManagementResponse management(MerchantProfileEntity merchant) {
        String logoUrl = merchant.getLogoMediaId() == null || mediaAssets == null || urls == null ? null
                : mediaAssets.findById(merchant.getLogoMediaId())
                        .filter(asset -> asset.getMediaStatus() == MediaStatus.READY)
                        .map(asset -> urls.publicUrl(asset).toString())
                        .orElse(null);
        String coverUrl = merchant.getCoverMediaId() == null || mediaAssets == null || urls == null ? null
                : mediaAssets.findById(merchant.getCoverMediaId())
                        .filter(asset -> asset.getMediaStatus() == MediaStatus.READY)
                        .map(asset -> urls.publicUrl(asset).toString())
                        .orElse(null);
        return new MerchantManagementResponse(merchant.getId(), merchant.getName(), merchant.getSlug(),
                merchant.getDescription(), merchant.getLogoMediaId(), merchant.getCoverMediaId(),
                merchant.getMerchantStatus(), merchant.getVersion(), logoUrl, coverUrl);
    }
}
