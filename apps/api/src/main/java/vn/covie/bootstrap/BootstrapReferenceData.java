package vn.covie.bootstrap;

import java.util.List;

public record BootstrapReferenceData(
        List<LocationAreaSeed> locationAreas,
        List<CategorySeed> categories) {

    public BootstrapReferenceData {
        locationAreas = locationAreas == null ? List.of() : List.copyOf(locationAreas);
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    public record LocationAreaSeed(
            String parentSlug,
            String slug,
            String name,
            String areaType,
            int sortOrder) {}

    public record CategorySeed(
            String parentSlug,
            String slug,
            String name,
            String catalogType,
            int sortOrder) {}
}
