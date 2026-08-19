package vn.covie.catalog.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name = "category_attributes")
public class CategoryAttributeEntity {
    @Id private UUID id; @Column(name="category_id", nullable=false) private UUID categoryId;
    @Column(nullable=false,length=120) private String name; @Column(name="attribute_key",nullable=false,length=100) private String attributeKey;
    @Enumerated(EnumType.STRING) @Column(name="data_type",nullable=false,length=30) private CategoryAttributeType dataType;
    @Column(name="is_required",nullable=false) private boolean required; @Column(name="is_filterable",nullable=false) private boolean filterable; @Column(name="sort_order",nullable=false) private int sortOrder;
    protected CategoryAttributeEntity() {}
    public static CategoryAttributeEntity create(UUID id, UUID categoryId, String name, String attributeKey, CategoryAttributeType dataType, boolean required, boolean filterable, int sortOrder) { CategoryAttributeEntity value=new CategoryAttributeEntity(); value.id=id; value.categoryId=categoryId; value.name=name; value.attributeKey=attributeKey; value.dataType=dataType; value.required=required; value.filterable=filterable; value.sortOrder=sortOrder; return value; }
    public UUID getId(){return id;} public UUID getCategoryId(){return categoryId;} public String getName(){return name;} public String getAttributeKey(){return attributeKey;} public CategoryAttributeType getDataType(){return dataType;} public boolean isRequired(){return required;} public boolean isFilterable(){return filterable;} public int getSortOrder(){return sortOrder;}
}
