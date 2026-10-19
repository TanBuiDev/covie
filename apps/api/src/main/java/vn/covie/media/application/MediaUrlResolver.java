package vn.covie.media.application;

import java.net.URI;
import org.springframework.stereotype.Component;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.storage.StorageProperties;

@Component
public class MediaUrlResolver {
    private final StorageProperties properties;
    private final CatalogVideoUploadPolicy videos;
    public MediaUrlResolver(StorageProperties properties,CatalogVideoUploadPolicy videos) { this.properties = properties; this.videos=videos; }
    public boolean catalogVideoEnabled() { return videos.enabled(); }
    public boolean includeCatalogMedia(MediaAssetEntity media) {
        return media.getPurpose()!=vn.covie.media.model.MediaPurpose.CATALOG_VIDEO || (videos.enabled() && media.getDeletedAt()==null);
    }
    public URI publicUrl(MediaAssetEntity media) {
        if (media.getMediaStatus() != MediaStatus.READY) throw new IllegalStateException("Only READY media has public URL");
        if (media.getPurpose()==vn.covie.media.model.MediaPurpose.CATALOG_VIDEO) {
            videos.requireEnabled();
            return videos.contentUrl(media.getId());
        }
        if (properties.publicBaseUrl() == null) throw new IllegalStateException("Media public base URL is not configured");
        String base = properties.publicBaseUrl().toString().replaceAll("/+$", "");
        return URI.create(base + "/" + media.getStorageKey());
    }
}
