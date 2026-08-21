package vn.covie.delivery.persistence;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCoverageEvaluationRepository {
    public record Match(UUID coverageId, boolean radiusEnabled, Integer radiusMeters, boolean areaEnabled,
            long distanceMeters, boolean areaMatch) {}
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcCoverageEvaluationRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Match> find(UUID locationId, String coverageType, double latitude, double longitude, UUID areaId) {
        String sql = """
            with recursive ancestors as (
                select id, parent_id from location_areas where id = :areaId
                union all
                select la.id, la.parent_id from location_areas la join ancestors a on la.id = a.parent_id
            )
            select dc.id, dc.radius_enabled, dc.radius_meters, dc.area_enabled,
                   round(st_distance(ml.geo_point, st_setsrid(st_makepoint(:lng, :lat), 4326)::geography))::bigint as distance_meters,
                   case when dc.area_enabled and :areaId is not null then exists (
                       select 1 from delivery_coverage_areas dca join ancestors a on a.id = dca.location_area_id
                       where dca.delivery_coverage_id = dc.id
                   ) else false end as area_match
            from delivery_coverages dc
            join merchant_locations ml on ml.id = dc.merchant_location_id
            where dc.merchant_location_id = :locationId
              and dc.coverage_type = :coverageType
              and dc.coverage_status = 'ACTIVE'
            order by dc.id
            limit 1
            """;
        Map<String, Object> params = new HashMap<>();
        params.put("locationId", locationId); params.put("coverageType", coverageType);
        params.put("lat", latitude); params.put("lng", longitude); params.put("areaId", areaId);
        return jdbc.query(sql, params, rs -> rs.next() ? Optional.of(new Match(rs.getObject("id", UUID.class),
                rs.getBoolean("radius_enabled"), (Integer) rs.getObject("radius_meters"), rs.getBoolean("area_enabled"),
                rs.getLong("distance_meters"), rs.getBoolean("area_match"))) : Optional.empty());
    }
}
