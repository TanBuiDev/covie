package vn.covie.catalog.model;
import jakarta.persistence.*;
import vn.covie.media.model.MediaAssetEntity;
import vn.covie.media.model.MediaPurpose;
@Entity @Table(name="catalog_item_media") public class CatalogItemMediaEntity { @EmbeddedId private CatalogItemMediaId id; @MapsId("catalogItemId") @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="catalog_item_id",nullable=false) private CatalogItemEntity catalogItem; @MapsId("mediaAssetId") @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="media_asset_id",nullable=false) private MediaAssetEntity mediaAsset; @Column(name="media_type",nullable=false,length=20) private String mediaType; @Column(name="sort_order",nullable=false) private int sortOrder; @Column(name="is_primary",nullable=false) private boolean primary; protected CatalogItemMediaEntity(){} public static CatalogItemMediaEntity image(CatalogItemEntity item,MediaAssetEntity asset,int sortOrder,boolean primary){CatalogItemMediaEntity media=new CatalogItemMediaEntity();media.id=new CatalogItemMediaId(item.getId(),asset.getId());media.catalogItem=item;media.mediaAsset=asset;media.mediaType="IMAGE";media.sortOrder=sortOrder;media.primary=primary;return media;} public void reorder(int sortOrder, boolean primary){this.sortOrder=sortOrder;this.primary=primary;} public CatalogItemMediaId getId(){return id;} public MediaAssetEntity getMediaAsset(){return mediaAsset;} public String getMediaType(){return mediaType;} public int getSortOrder(){return sortOrder;} public boolean isPrimary(){return primary;}
    public static CatalogItemMediaEntity video(CatalogItemEntity item,MediaAssetEntity asset,int sortOrder) {
        if (asset.getPurpose()!=MediaPurpose.CATALOG_VIDEO) throw new IllegalArgumentException("Catalog video purpose required.");
        var media = image(item,asset,sortOrder,false);
        media.mediaType="VIDEO";
        return media;
    }
}
