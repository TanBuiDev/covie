package vn.covie.notifications.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcNeedAudienceRepository implements NeedAudienceRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcNeedAudienceRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Override public List<NeedAudienceLocation> findCandidateLocations(UUID needId, int limit) {
        String sql = """
            with recursive category_paths as (
              select c.id descendant_id, c.id ancestor_id from categories c where c.status='ACTIVE' and c.deleted_at is null
              union all select cp.descendant_id, p.parent_id from category_paths cp join categories p on p.id=cp.ancestor_id where p.parent_id is not null
            ) select distinct ml.merchant_profile_id, ml.id merchant_location_id,
              st_distance(n.geo_point,ml.geo_point)::bigint distance_meters
            from need_requests n join merchant_locations ml on ml.location_status='ACTIVE' and ml.deleted_at is null
              join merchant_profiles mp on mp.id=ml.merchant_profile_id and mp.merchant_status='ACTIVE'
                and mp.moderation_status in ('NOT_REQUIRED','APPROVED') and mp.deleted_at is null
              join location_variants lv on lv.merchant_location_id=ml.id and lv.is_available and lv.deleted_at is null
              join variants v on v.id=lv.variant_id and v.variant_status='ACTIVE' and v.deleted_at is null
              join catalog_items ci on ci.id=v.catalog_item_id and ci.item_type=n.need_type and ci.listing_status='ACTIVE'
                and ci.moderation_status in ('NOT_REQUIRED','APPROVED') and ci.deleted_at is null
              join category_paths cp on cp.descendant_id=ci.primary_category_id and cp.ancestor_id=n.category_id
              left join inventory_balances ib on ib.location_variant_id=lv.id
            where n.id=:needId and n.request_status='ACTIVE' and n.moderation_status in ('NOT_REQUIRED','APPROVED') and n.expires_at>:now
              and st_dwithin(n.geo_point,ml.geo_point,n.search_radius_meters)
              and (n.need_type='SERVICE' or lv.inventory_mode='UNTRACKED' or (lv.inventory_mode='TRACKED' and ib.stock_on_hand-ib.reserved_quantity>0))
            order by distance_meters asc, ml.merchant_profile_id asc, ml.id asc limit :limit
            """;
        var p = new MapSqlParameterSource().addValue("needId", needId).addValue("now", java.sql.Timestamp.from(Instant.now())).addValue("limit", limit);
        return jdbc.query(sql, p, (rs, i) -> new NeedAudienceLocation(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getLong(3)));
    }
}
