package vn.covie.catalog.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Entity @Table(name="catalog_item_attribute_values")
public class CatalogItemAttributeValueEntity {
    @Id private UUID id; @Column(name="catalog_item_id",nullable=false) private UUID catalogItemId; @Column(name="category_attribute_id",nullable=false) private UUID categoryAttributeId; @Column(name="value_text") private String valueText; @Column(name="value_number",precision=20,scale=6) private BigDecimal valueNumber; @Column(name="value_boolean") private Boolean valueBoolean; @Column(name="attribute_option_id") private UUID attributeOptionId;
    protected CatalogItemAttributeValueEntity() {}
    public static CatalogItemAttributeValueEntity create(UUID id, UUID catalogItemId, UUID categoryAttributeId, String valueText, BigDecimal valueNumber, Boolean valueBoolean, UUID attributeOptionId){CatalogItemAttributeValueEntity value=new CatalogItemAttributeValueEntity();value.id=id;value.catalogItemId=catalogItemId;value.categoryAttributeId=categoryAttributeId;value.valueText=valueText;value.valueNumber=valueNumber;value.valueBoolean=valueBoolean;value.attributeOptionId=attributeOptionId;return value;}
    public UUID getId(){return id;} public UUID getCatalogItemId(){return catalogItemId;} public UUID getCategoryAttributeId(){return categoryAttributeId;} public String getValueText(){return valueText;} public BigDecimal getValueNumber(){return valueNumber;} public Boolean getValueBoolean(){return valueBoolean;} public UUID getAttributeOptionId(){return attributeOptionId;}
}
