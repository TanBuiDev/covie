package vn.covie.media.api.dto;

import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
import vn.covie.media.model.MediaStatus;

public record CatalogVideoMetadata(int width,int height,long durationMillis,String codec) {
    public static CatalogVideoMetadata from(MediaAssetEntity asset) {
        if (asset.getPurpose()!=MediaPurpose.CATALOG_VIDEO || asset.getMediaStatus()!=MediaStatus.READY) return null;
        return new CatalogVideoMetadata(asset.getWidth(),asset.getHeight(),asset.getVideoDurationMillis(),asset.getVideoCodec());
    }
}
