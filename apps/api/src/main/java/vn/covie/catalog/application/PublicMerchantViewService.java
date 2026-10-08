package vn.covie.catalog.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.covie.catalog.api.dto.publicview.PublicMediaResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantContactResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantCapabilitiesResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantResponse;
import vn.covie.catalog.api.dto.publicview.PublicMerchantSummary;
import vn.covie.catalog.api.dto.publicview.ResolvedMerchantLocationResponse;
import vn.covie.catalog.persistence.PublicMerchantCatalogRepository;
import vn.covie.location.application.LocationResolutionSource;
import vn.covie.media.application.MediaUrlResolver;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.persistence.MediaAssetRepository;
import vn.covie.merchants.model.ContactStatus;
import vn.covie.merchants.model.ContactType;
import vn.covie.merchants.model.MerchantContactMethodEntity;
import vn.covie.merchants.model.MerchantLocationEntity;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.model.MerchantModerationStatus;
import vn.covie.merchants.model.MerchantProfileEntity;
import vn.covie.merchants.model.MerchantStatus;
import vn.covie.merchants.persistence.MerchantContactMethodRepository;
import vn.covie.merchants.persistence.MerchantLocationRepository;
import vn.covie.merchants.persistence.MerchantProfileRepository;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;
import vn.covie.reputation.application.ReputationReadService;

@Service
public class PublicMerchantViewService {
    private final MerchantProfileRepository merchants;
    private final MerchantLocationRepository locations;
    private final MerchantContactMethodRepository contacts;
    private final MediaAssetRepository media;
    private final MediaUrlResolver urls;
    private final ReputationReadService reputation;
    private final PublicMerchantCatalogRepository publicCatalog;

    public PublicMerchantViewService(MerchantProfileRepository merchants, MerchantLocationRepository locations,
            MerchantContactMethodRepository contacts, MediaAssetRepository media, MediaUrlResolver urls,
            ReputationReadService reputation, PublicMerchantCatalogRepository publicCatalog) {
        this.merchants = merchants;
        this.locations = locations;
        this.contacts = contacts;
        this.media = media;
        this.urls = urls;
        this.reputation = reputation;
        this.publicCatalog = publicCatalog;
    }

    public PublicMerchantResponse get(UUID merchantId) {
        return toResponse(requireVisible(merchantId));
    }

    public MerchantProfileEntity requireVisible(UUID merchantId) {
        MerchantProfileEntity merchant = merchants.findByIdAndDeletedAtIsNull(merchantId)
                .orElseThrow(PublicMerchantViewService::notFound);
        if (!isVisible(merchant)) {
            throw notFound();
        }
        return merchant;
    }

    public PublicMerchantResponse toResponse(MerchantProfileEntity merchant) {
        List<ResolvedMerchantLocationResponse> activeLocations = locations
                .findAllByMerchantProfileIdAndDeletedAtIsNullOrderByNameAscIdAsc(merchant.getId()).stream()
                .filter(location -> location.getLocationStatus() == MerchantLocationStatus.ACTIVE)
                .map(location -> locationResponse(location, null, null))
                .toList();
        List<PublicMerchantContactResponse> publicContacts = contacts
                .findAllByMerchantProfileIdAndStatusOrderByCreatedAtAscIdAsc(merchant.getId(), ContactStatus.ACTIVE).stream()
                .filter(contact -> contact.isPublicContact())
                .map(this::publicContact)
                .flatMap(Optional::stream)
                .toList();
        return new PublicMerchantResponse(merchant.getId(), merchant.getName(), merchant.getSlug(),
                merchant.getDescription(), publicMedia(merchant.getLogoMediaId()), publicMedia(merchant.getCoverMediaId()),
                activeLocations, publicContacts, reputation.merchant(merchant.getId()),
                new PublicMerchantCapabilitiesResponse(publicCatalog.findCatalogTypes(merchant.getId())));
    }

    public PublicMerchantSummary summary(MerchantProfileEntity merchant) {
        return new PublicMerchantSummary(merchant.getId(), merchant.getName(), merchant.getSlug());
    }

    public ResolvedMerchantLocationResponse locationResponse(MerchantLocationEntity location, Double distanceMeters,
            LocationResolutionSource source) {
        return new ResolvedMerchantLocationResponse(location.getId(), location.getName(), location.getAddressText(),
                distanceMeters, source);
    }

    public static boolean isVisible(MerchantProfileEntity merchant) {
        return merchant.getMerchantStatus() == MerchantStatus.ACTIVE
                && (merchant.getModerationStatus() == MerchantModerationStatus.NOT_REQUIRED
                        || merchant.getModerationStatus() == MerchantModerationStatus.APPROVED);
    }

    private PublicMediaResponse publicMedia(UUID mediaId) {
        if (mediaId == null) {
            return null;
        }
        return media.findById(mediaId)
                .filter(asset -> asset.getMediaStatus() == MediaStatus.READY)
                .map(asset -> new PublicMediaResponse(asset.getId(), asset.getMimeType(), urls.publicUrl(asset)))
                .orElse(null);
    }

    private Optional<PublicMerchantContactResponse> publicContact(MerchantContactMethodEntity contact) {
        PublicMediaResponse qrMedia = publicMedia(contact.getQrMediaId());
        if (contact.getContactType() == ContactType.ZALO_QR && qrMedia == null) {
            return Optional.empty();
        }
        return Optional.of(new PublicMerchantContactResponse(contact.getId(), contact.getMerchantLocationId(),
                contact.getContactType(), contact.getValue(), qrMedia, contact.isPrimary()));
    }

    private static ApiException notFound() {
        return new ApiException(ErrorCode.RESOURCE_NOT_FOUND, HttpStatus.NOT_FOUND, "Resource not found.");
    }
}
