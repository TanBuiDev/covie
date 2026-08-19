package vn.covie.catalog.model;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.UUID;
@Embeddable public class CatalogItemTagId implements Serializable { private UUID catalogItemId; private UUID tagId; protected CatalogItemTagId(){} public CatalogItemTagId(UUID catalogItemId,UUID tagId){this.catalogItemId=catalogItemId;this.tagId=tagId;} public UUID getCatalogItemId(){return catalogItemId;} public UUID getTagId(){return tagId;} @Override public boolean equals(Object other){return other instanceof CatalogItemTagId value && java.util.Objects.equals(catalogItemId,value.catalogItemId)&&java.util.Objects.equals(tagId,value.tagId);}@Override public int hashCode(){return java.util.Objects.hash(catalogItemId,tagId);} }
