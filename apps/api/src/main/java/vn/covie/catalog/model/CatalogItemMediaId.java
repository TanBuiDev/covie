package vn.covie.catalog.model;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
@Embeddable public class CatalogItemMediaId implements Serializable { private UUID catalogItemId; private UUID mediaAssetId; protected CatalogItemMediaId(){} public CatalogItemMediaId(UUID catalogItemId,UUID mediaAssetId){this.catalogItemId=catalogItemId;this.mediaAssetId=mediaAssetId;} public UUID getCatalogItemId(){return catalogItemId;} public UUID getMediaAssetId(){return mediaAssetId;} @Override public boolean equals(Object other){return other instanceof CatalogItemMediaId value&&java.util.Objects.equals(catalogItemId,value.catalogItemId)&&java.util.Objects.equals(mediaAssetId,value.mediaAssetId);}@Override public int hashCode(){return java.util.Objects.hash(catalogItemId,mediaAssetId);} }
