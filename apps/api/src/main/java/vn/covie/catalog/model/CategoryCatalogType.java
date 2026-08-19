package vn.covie.catalog.model;
public enum CategoryCatalogType {
    PRODUCT, SERVICE, BOTH;
    public boolean supports(CatalogItemType type) { return this == BOTH || name().equals(type.name()); }
}
