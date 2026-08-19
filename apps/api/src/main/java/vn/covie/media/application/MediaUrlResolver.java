package vn.covie.media.application;

import java.net.URI;
import org.springframework.stereotype.Component;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaStatus;
import vn.covie.media.storage.StorageProperties;

@Component
public class MediaUrlResolver {
    private final StorageProperties properties;
    public MediaUrlResolver(StorageProperties properties) { this.properties = properties; }
    public URI publicUrl(MediaAssetEntity media) {
        if (media.getMediaStatus() != MediaStatus.READY) throw new IllegalStateException("Only READY media has public URL");
        if (properties.publicBaseUrl() == null) throw new IllegalStateException("Media public base URL is not configured");
        String base = properties.publicBaseUrl().toString().replaceAll("/+$", "");
        return URI.create(base + "/" + media.getStorageKey());
    }
}
