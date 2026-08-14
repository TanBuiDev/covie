package vn.covie.catalog.model;

import jakarta.persistence.*;
import java.util.UUID;

@Entity @Table(name="category_attribute_options")
public class CategoryAttributeOptionEntity {
    @Id private UUID id; @Column(name="category_attribute_id",nullable=false) private UUID categoryAttributeId; @Column(name="option_value",nullable=false,length=180) private String optionValue; @Column(name="sort_order",nullable=false) private int sortOrder;
    protected CategoryAttributeOptionEntity() {}
    public static CategoryAttributeOptionEntity create(UUID id,UUID categoryAttributeId,String optionValue,int sortOrder){CategoryAttributeOptionEntity value=new CategoryAttributeOptionEntity();value.id=id;value.categoryAttributeId=categoryAttributeId;value.optionValue=optionValue;value.sortOrder=sortOrder;return value;}
    public UUID getId(){return id;} public UUID getCategoryAttributeId(){return categoryAttributeId;} public String getOptionValue(){return optionValue;} public int getSortOrder(){return sortOrder;}
}
