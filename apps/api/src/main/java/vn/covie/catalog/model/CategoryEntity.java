package vn.covie.catalog.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name = "categories")
public class CategoryEntity {
    @Id private UUID id;
    @Column(name = "parent_id") private UUID parentId;
    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 180) private String slug;
    @Enumerated(EnumType.STRING) @Column(name = "catalog_type", nullable = false, length = 20) private CategoryCatalogType catalogType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CategoryStatus status;
    @Column(name = "sort_order", nullable = false) private int sortOrder;
    @Column(name = "deleted_at") private Instant deletedAt;
    @Version @Column(nullable = false) private long version;
    protected CategoryEntity() {}
    public static CategoryEntity create(UUID id, UUID parentId, String name, String slug, CategoryCatalogType catalogType, CategoryStatus status, int sortOrder) {
        CategoryEntity category = new CategoryEntity(); category.id=id; category.parentId=parentId; category.name=name; category.slug=slug; category.catalogType=catalogType; category.status=status; category.sortOrder=sortOrder; return category;
    }
    public UUID getId() { return id; } public UUID getParentId() { return parentId; } public String getName() { return name; } public String getSlug() { return slug; } public CategoryCatalogType getCatalogType() { return catalogType; } public CategoryStatus getStatus() { return status; } public int getSortOrder() { return sortOrder; } public Instant getDeletedAt() { return deletedAt; } public long getVersion() { return version; }
}
