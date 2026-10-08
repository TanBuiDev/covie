package vn.covie.needs.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.covie.needs.model.NeedType;

@Repository
public class JdbcNeedEligibleCandidateRepository implements NeedEligibleCandidateRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNeedEligibleCandidateRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<NeedEligibleCandidateRow> findCandidates(UUID merchantId, UUID locationId,
            NeedType needType, NeedEligibleCandidateKey after, int limit) {
        String sql = """
                select ci.id catalog_item_id, v.id variant_id, lv.id location_variant_id,
                       ci.name catalog_item_name, v.variant_name
                from location_variants lv
                join variants v on v.id=lv.variant_id and v.deleted_at is null and v.variant_status='ACTIVE'
                join catalog_items ci on ci.id=v.catalog_item_id and ci.deleted_at is null
                    and ci.listing_status='ACTIVE' and ci.moderation_status in ('NOT_REQUIRED','APPROVED')
                where lv.merchant_location_id=:locationId and lv.deleted_at is null and lv.is_available=true
                  and ci.merchant_profile_id=:merchantId and ci.item_type=:needType
                  and (:afterAbsent=true or (ci.id,v.id,lv.id)>(:afterItemId,:afterVariantId,:afterOfferId))
                order by ci.id,v.id,lv.id limit :limit
                """;
        UUID zero = new UUID(0, 0);
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("merchantId", merchantId).addValue("locationId", locationId)
                .addValue("needType", needType.name()).addValue("afterAbsent", after == null)
                .addValue("afterItemId", after == null ? zero : after.catalogItemId())
                .addValue("afterVariantId", after == null ? zero : after.variantId())
                .addValue("afterOfferId", after == null ? zero : after.locationVariantId())
                .addValue("limit", limit);
        return jdbc.query(sql, params, (rs, index) -> new NeedEligibleCandidateRow(
                (UUID) rs.getObject("catalog_item_id"), (UUID) rs.getObject("variant_id"),
                (UUID) rs.getObject("location_variant_id"), rs.getString("catalog_item_name"),
                rs.getString("variant_name")));
    }
}
