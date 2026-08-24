package vn.covie.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PilotReferenceDataBootstrapRunner implements ApplicationRunner {
    private final Environment environment;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcTemplate jdbc;

    public PilotReferenceDataBootstrapRunner(
            Environment environment,
            ResourceLoader resourceLoader,
            ObjectMapper objectMapper,
            NamedParameterJdbcTemplate jdbc) {
        this.environment = environment;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws Exception {
        PilotBootstrapProperties properties = PilotBootstrapProperties.from(environment);
        if (!properties.referenceDataEnabled()) return;

        requirePrerequisiteV013();
        requireExpectedSchema();

        BootstrapReferenceData data;
        try (InputStream input = resourceLoader.getResource(properties.referenceDataPath()).getInputStream()) {
            data = objectMapper.readValue(input, BootstrapReferenceData.class);
        }

        Map<String, UUID> areaIds = new HashMap<>();
        for (BootstrapReferenceData.LocationAreaSeed seed : data.locationAreas()) {
            UUID parentId = parent(seed.parentSlug(), areaIds, "location area");
            UUID id = upsertLocationArea(seed, parentId);
            areaIds.put(seed.slug(), id);
        }

        Map<String, UUID> categoryIds = new HashMap<>();
        for (BootstrapReferenceData.CategorySeed seed : data.categories()) {
            UUID parentId = parent(seed.parentSlug(), categoryIds, "category");
            UUID id = upsertCategory(seed, parentId);
            categoryIds.put(seed.slug(), id);
        }
    }

    private UUID upsertLocationArea(BootstrapReferenceData.LocationAreaSeed seed, UUID parentId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("parentId", parentId)
                .addValue("slug", seed.slug())
                .addValue("name", seed.name())
                .addValue("areaType", seed.areaType())
                .addValue("sortOrder", seed.sortOrder());
        List<UUID> ids = jdbc.query(
                """
                select id from location_areas
                 where parent_id is not distinct from :parentId
                   and slug = :slug
                   and deleted_at is null
                """, params, (rs, rowNum) -> rs.getObject(1, UUID.class));
        if (ids.size() > 1) throw new IllegalStateException("Duplicate location bootstrap key: " + seed.slug());
        if (ids.size() == 1) {
            UUID id = ids.getFirst();
            params.addValue("id", id);
            jdbc.update(
                    """
                    update location_areas
                       set name = :name, area_type = :areaType, sort_order = :sortOrder, updated_at = now()
                     where id = :id
                    """, params);
            return id;
        }
        UUID id = databaseUuidV7();
        params.addValue("id", id);
        jdbc.update(
                """
                insert into location_areas(id, parent_id, slug, name, area_type, status, sort_order, created_at, updated_at)
                values (:id, :parentId, :slug, :name, :areaType, 'ACTIVE', :sortOrder, now(), now())
                """, params);
        return id;
    }

    private UUID upsertCategory(BootstrapReferenceData.CategorySeed seed, UUID parentId) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("parentId", parentId)
                .addValue("slug", seed.slug())
                .addValue("name", seed.name())
                .addValue("catalogType", seed.catalogType())
                .addValue("sortOrder", seed.sortOrder());
        List<UUID> ids = jdbc.query(
                """
                select id from categories
                 where parent_id is not distinct from :parentId
                   and slug = :slug
                   and deleted_at is null
                """, params, (rs, rowNum) -> rs.getObject(1, UUID.class));
        if (ids.size() > 1) throw new IllegalStateException("Duplicate category bootstrap key: " + seed.slug());
        if (ids.size() == 1) {
            UUID id = ids.getFirst();
            params.addValue("id", id);
            jdbc.update(
                    """
                    update categories
                       set name = :name, catalog_type = :catalogType, status = 'ACTIVE',
                           sort_order = :sortOrder, updated_at = now()
                     where id = :id
                    """, params);
            return id;
        }
        UUID id = databaseUuidV7();
        params.addValue("id", id);
        jdbc.update(
                """
                insert into categories(id, parent_id, slug, name, catalog_type, status, sort_order, created_at, updated_at)
                values (:id, :parentId, :slug, :name, :catalogType, 'ACTIVE', :sortOrder, now(), now())
                """, params);
        return id;
    }

    private UUID databaseUuidV7() {
        return jdbc.getJdbcTemplate().queryForObject("select uuidv7()", UUID.class);
    }

    private static UUID parent(String parentSlug, Map<String, UUID> known, String kind) {
        if (parentSlug == null || parentSlug.isBlank()) return null;
        UUID id = known.get(parentSlug);
        if (id == null) throw new IllegalStateException("Bootstrap " + kind + " parent must appear first: " + parentSlug);
        return id;
    }

    private void requirePrerequisiteV013() {
        Integer version = jdbc.getJdbcTemplate().queryForObject(
                """
                select coalesce(max(nullif(regexp_replace(version, '[^0-9].*$', ''), '')::int), 0)
                  from flyway_schema_history where success
                """, Integer.class);
        if (version == null || version < 13) {
            throw new IllegalStateException("Pilot bootstrap requires Plan 09 prerequisite Flyway V013 before it can run");
        }
    }

    private void requireExpectedSchema() {
        for (String table : List.of("location_areas", "categories")) {
            Boolean exists = jdbc.getJdbcTemplate().queryForObject(
                    "select to_regclass(?) is not null", Boolean.class, "public." + table);
            if (!Boolean.TRUE.equals(exists)) {
                throw new IllegalStateException("Pilot bootstrap expected table missing after V013: " + table);
            }
        }
    }
}
