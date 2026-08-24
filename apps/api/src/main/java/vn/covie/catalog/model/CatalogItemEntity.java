package vn.covie.catalog.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="catalog_items")
public class CatalogItemEntity {
    @Id private UUID id; @Column(name="merchant_profile_id",nullable=false) private UUID merchantProfileId;
    @Enumerated(EnumType.STRING) @Column(name="item_type",nullable=false,length=20) private CatalogItemType itemType;
    @Enumerated(EnumType.STRING) @Column(name="listing_kind",nullable=false,length=20) private ListingKind listingKind;
    @Column(name="primary_category_id",nullable=false) private UUID primaryCategoryId; @Column(nullable=false,length=220) private String name; @Column(nullable=false,length=240) private String slug; @Column private String description;
    @Enumerated(EnumType.STRING) @Column(name="listing_status",nullable=false,length=30) private ListingStatus listingStatus;
    @Enumerated(EnumType.STRING) @Column(name="moderation_status",nullable=false,length=30) private CatalogModerationStatus moderationStatus;
    @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="updated_at",nullable=false) private Instant updatedAt; @Column(name="deleted_at") private Instant deletedAt; @Version @Column(nullable=false) private long version;
    protected CatalogItemEntity() {}
    public static CatalogItemEntity create(UUID id,UUID merchantProfileId,CatalogItemType itemType,ListingKind listingKind,UUID primaryCategoryId,String name,String slug,String description,Instant now){if(itemType==CatalogItemType.SERVICE&&listingKind==ListingKind.RESALE)throw new IllegalArgumentException("Only products can be resale listings.");CatalogItemEntity item=new CatalogItemEntity();item.id=id;item.merchantProfileId=merchantProfileId;item.itemType=itemType;item.listingKind=listingKind;item.primaryCategoryId=primaryCategoryId;item.name=name;item.slug=slug;item.description=description;item.listingStatus=ListingStatus.DRAFT;item.moderationStatus=CatalogModerationStatus.NOT_REQUIRED;item.createdAt=now;item.updatedAt=now;return item;}
    public void publish(Instant now){listingStatus=ListingStatus.ACTIVE;moderationStatus=CatalogModerationStatus.NOT_REQUIRED;updatedAt=now;}
    public void requireModeration(Instant now){if(moderationStatus!=CatalogModerationStatus.NOT_REQUIRED)throw new IllegalStateException("Catalog item moderation is already set.");moderationStatus=CatalogModerationStatus.PENDING;updatedAt=now;}
    public void approveModeration(Instant now){if(moderationStatus!=CatalogModerationStatus.PENDING)throw new IllegalStateException("Catalog moderation is not pending.");moderationStatus=CatalogModerationStatus.APPROVED;listingStatus=ListingStatus.ACTIVE;updatedAt=now;}
    public void rejectModeration(Instant now){if(moderationStatus!=CatalogModerationStatus.PENDING)throw new IllegalStateException("Catalog moderation is not pending.");moderationStatus=CatalogModerationStatus.REJECTED;updatedAt=now;}
    public void hideModeration(Instant now){if(moderationStatus!=CatalogModerationStatus.NOT_REQUIRED&&moderationStatus!=CatalogModerationStatus.APPROVED)throw new IllegalStateException("Catalog item is not visible.");moderationStatus=CatalogModerationStatus.HIDDEN;updatedAt=now;}
    public void restoreModeration(Instant now){if(moderationStatus!=CatalogModerationStatus.HIDDEN&&moderationStatus!=CatalogModerationStatus.REJECTED)throw new IllegalStateException("Catalog item is not restorable.");moderationStatus=CatalogModerationStatus.APPROVED;updatedAt=now;}
    public void pause(Instant now){listingStatus=ListingStatus.PAUSED;updatedAt=now;} public void activate(Instant now){listingStatus=ListingStatus.ACTIVE;updatedAt=now;} public void delete(Instant now){listingStatus=ListingStatus.DELETED;deletedAt=now;updatedAt=now;}
    public void update(UUID categoryId,String name,String description,Instant now){primaryCategoryId=categoryId;this.name=name;this.description=description;updatedAt=now;}
    public UUID getId(){return id;} public UUID getMerchantProfileId(){return merchantProfileId;} public CatalogItemType getItemType(){return itemType;} public ListingKind getListingKind(){return listingKind;} public UUID getPrimaryCategoryId(){return primaryCategoryId;} public String getName(){return name;} public String getSlug(){return slug;} public String getDescription(){return description;} public ListingStatus getListingStatus(){return listingStatus;} public CatalogModerationStatus getModerationStatus(){return moderationStatus;} public Instant getCreatedAt(){return createdAt;} public Instant getUpdatedAt(){return updatedAt;} public Instant getDeletedAt(){return deletedAt;} public long getVersion(){return version;}
}
