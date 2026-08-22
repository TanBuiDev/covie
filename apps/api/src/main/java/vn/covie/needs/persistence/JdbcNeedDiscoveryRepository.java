package vn.covie.needs.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.covie.needs.application.NeedDiscoveryCursor;
import vn.covie.needs.model.NeedType;

@Repository
public class JdbcNeedDiscoveryRepository implements NeedDiscoveryRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcNeedDiscoveryRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<NeedMatchRow> findCandidates(UUID merchantId, Set<UUID> allowedLocationIds, UUID requestedLocationId,
            Instant now, NeedDiscoveryCursor after, int candidateLimit) {
        if (allowedLocationIds.isEmpty()) return List.of();
        Set<UUID> locationIds = requestedLocationId == null ? allowedLocationIds : Set.of(requestedLocationId);
        String sql = """
                with recursive category_paths as (
                    select c.id descendant_id, c.id ancestor_id from categories c where c.status = 'ACTIVE' and c.deleted_at is null
                    union all
                    select cp.descendant_id, parent.parent_id from category_paths cp join categories parent on parent.id = cp.ancestor_id
                    where parent.parent_id is not null
                ), candidate as (
                    select n.id need_id, n.need_type, n.category_id, n.title, n.description, n.expires_at,
                           ml.id merchant_location_id, ml.name merchant_location_name, area.name buyer_area_label,
                           st_distance(n.geo_point, ml.geo_point)::bigint distance_meters,
                           max(greatest(coalesce(ts_rank(ci.search_vector, websearch_to_tsquery('simple', unaccent(n.query_text))), 0),
                                        similarity(unaccent(lower(ci.name)), unaccent(lower(n.query_text))))) text_rank,
                           ml.pickup_enabled,
                           exists(select 1 from need_responses nr where nr.need_request_id=n.id and nr.merchant_location_id=ml.id and nr.response_status='ACTIVE') already_responded
                    from need_requests n
                    join merchant_locations ml on ml.merchant_profile_id=:merchantId and ml.id in (:locationIds)
                        and ml.location_status='ACTIVE' and ml.deleted_at is null
                    join location_variants lv on lv.merchant_location_id=ml.id and lv.is_available=true and lv.deleted_at is null
                    join variants v on v.id=lv.variant_id and v.variant_status='ACTIVE' and v.deleted_at is null
                    join catalog_items ci on ci.id=v.catalog_item_id and ci.item_type=n.need_type and ci.listing_status='ACTIVE'
                        and ci.moderation_status in ('NOT_REQUIRED','APPROVED') and ci.deleted_at is null
                    join category_paths cp on cp.descendant_id=ci.primary_category_id and cp.ancestor_id=n.category_id
                    left join location_areas area on area.id=n.location_area_id
                    left join inventory_balances ib on ib.location_variant_id=lv.id
                    where n.request_status='ACTIVE' and n.expires_at>:now and st_dwithin(n.geo_point,ml.geo_point,n.search_radius_meters)
                      and (n.need_type='SERVICE' or lv.inventory_mode='UNTRACKED' or (lv.inventory_mode='TRACKED' and coalesce(ib.stock_on_hand,0)>coalesce(ib.reserved_quantity,0)))
                    group by n.id,n.need_type,n.category_id,n.title,n.description,n.expires_at,ml.id,ml.name,area.name,ml.pickup_enabled
                ) select * from candidate
                where :cursorAbsent=true or text_rank < :rank or (text_rank=:rank and distance_meters>:distance)
                   or (text_rank=:rank and distance_meters=:distance and need_id>:needId)
                   or (text_rank=:rank and distance_meters=:distance and need_id=:needId and merchant_location_id>:locationId)
                order by text_rank desc,distance_meters asc,need_id asc,merchant_location_id asc limit :limit
                """;
        MapSqlParameterSource p = new MapSqlParameterSource().addValue("merchantId", merchantId).addValue("locationIds", locationIds)
                .addValue("now", now).addValue("cursorAbsent", after == null).addValue("rank", after == null ? 0d : after.textRank())
                .addValue("distance", after == null ? 0L : after.distanceMeters()).addValue("needId", after == null ? new UUID(0, 0) : after.needId())
                .addValue("locationId", after == null ? new UUID(0, 0) : after.merchantLocationId()).addValue("limit", candidateLimit);
        return jdbc.query(sql, p, (rs, row) -> new NeedMatchRow((UUID) rs.getObject("need_id"), NeedType.valueOf(rs.getString("need_type")),
                (UUID) rs.getObject("category_id"), rs.getString("title"), rs.getString("description"), rs.getTimestamp("expires_at").toInstant(),
                (UUID) rs.getObject("merchant_location_id"), rs.getString("merchant_location_name"), rs.getString("buyer_area_label"),
                rs.getDouble("text_rank"), rs.getLong("distance_meters"), rs.getBoolean("pickup_enabled"), rs.getBoolean("already_responded")));
    }
}
