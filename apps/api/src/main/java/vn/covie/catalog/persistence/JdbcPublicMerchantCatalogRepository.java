package vn.covie.catalog.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;

@Repository
public class JdbcPublicMerchantCatalogRepository implements PublicMerchantCatalogRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcPublicMerchantCatalogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Row> find(UUID merchantId, Instant cursorCreatedAt, UUID cursorItemId, int fetchLimit, Instant now) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("merchantId", merchantId)
                .addValue("cursorCreatedAt", cursorCreatedAt == null ? null : cursorCreatedAt.atOffset(ZoneOffset.UTC),
                        Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("cursorItemId", cursorItemId, Types.OTHER)
                .addValue("now", now.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("fetchLimit", fetchLimit);
        String sql = """
                select ci.id as item_id, ci.item_type, ci.listing_kind, ci.name, ci.slug, ci.created_at,
                       mp.id as merchant_id, mp.name as merchant_name, mp.slug as merchant_slug,
                       offer.location_id, offer.location_name, offer.address_text,
                       offer.pickup_enabled, offer.seller_delivery_enabled, offer.service_enabled,
                       offer.open_now, offer.min_price, offer.currency_code,
                       media.id as primary_media_id, media.mime_type as primary_media_mime_type,
                       media.storage_key as primary_media_storage_key
                from catalog_items ci
                join merchant_profiles mp on mp.id = ci.merchant_profile_id
                  and mp.merchant_status = 'ACTIVE'
                  and mp.moderation_status in ('NOT_REQUIRED', 'APPROVED')
                  and mp.deleted_at is null
                join lateral (
                    select ml.id as location_id, ml.name as location_name, ml.address_text,
                           ml.pickup_enabled, ml.seller_delivery_enabled, ml.service_enabled,
                           lv.price as min_price, ml.currency_code,
                           (
                             (not exists (
                                select 1 from merchant_location_hour_exceptions e
                                where e.merchant_location_id = ml.id and e.operation_type = 'SHOP'
                                  and e.local_date = timezone(ml.timezone, cast(:now as timestamptz))::date
                              ) and exists (
                                select 1 from merchant_location_hours h
                                where h.merchant_location_id = ml.id and h.operation_type = 'SHOP'
                                  and h.day_of_week = extract(isodow from timezone(ml.timezone, cast(:now as timestamptz)))
                                  and not h.is_closed
                                  and timezone(ml.timezone, cast(:now as timestamptz))::time >= h.open_time
                                  and timezone(ml.timezone, cast(:now as timestamptz))::time < h.close_time
                              )) or exists (
                                select 1 from merchant_location_hour_exceptions e
                                where e.merchant_location_id = ml.id and e.operation_type = 'SHOP'
                                  and e.local_date = timezone(ml.timezone, cast(:now as timestamptz))::date
                                  and not e.is_closed
                                  and timezone(ml.timezone, cast(:now as timestamptz))::time >= e.open_time
                                  and timezone(ml.timezone, cast(:now as timestamptz))::time < e.close_time
                              )
                           ) as open_now
                    from variants v
                    join location_variants lv on lv.variant_id = v.id and lv.is_available and lv.deleted_at is null
                    join merchant_locations ml on ml.id = lv.merchant_location_id
                      and ml.merchant_profile_id = ci.merchant_profile_id
                      and ml.location_status = 'ACTIVE' and ml.deleted_at is null
                    where v.catalog_item_id = ci.id and v.variant_status = 'ACTIVE' and v.deleted_at is null
                    order by lv.price asc, ml.id asc, lv.id asc
                    limit 1
                ) offer on true
                left join lateral (
                    select ma.id, ma.mime_type, ma.storage_key
                    from catalog_item_media cim
                    join media_assets ma on ma.id = cim.media_asset_id
                    where cim.catalog_item_id = ci.id and cim.is_primary
                      and ma.media_status = 'READY' and ma.deleted_at is null
                    order by cim.sort_order asc, ma.id asc
                    limit 1
                ) media on true
                where ci.merchant_profile_id = :merchantId
                  and ci.listing_status = 'ACTIVE'
                  and ci.moderation_status in ('NOT_REQUIRED', 'APPROVED')
                  and ci.deleted_at is null
                  and (cast(:cursorCreatedAt as timestamptz) is null
                       or (ci.created_at, ci.id) < (cast(:cursorCreatedAt as timestamptz), cast(:cursorItemId as uuid)))
                order by ci.created_at desc, ci.id desc
                limit :fetchLimit
                """;
        return jdbc.query(sql, parameters, this::map);
    }

    private Row map(ResultSet result, int ignored) throws SQLException {
        return new Row(
                result.getObject("item_id", UUID.class),
                CatalogItemType.valueOf(result.getString("item_type")),
                ListingKind.valueOf(result.getString("listing_kind")),
                result.getString("name"),
                result.getString("slug"),
                result.getTimestamp("created_at").toInstant(),
                result.getObject("merchant_id", UUID.class),
                result.getString("merchant_name"),
                result.getString("merchant_slug"),
                result.getObject("location_id", UUID.class),
                result.getString("location_name"),
                result.getString("address_text"),
                result.getBoolean("pickup_enabled"),
                result.getBoolean("seller_delivery_enabled"),
                result.getBoolean("service_enabled"),
                result.getBoolean("open_now"),
                result.getLong("min_price"),
                result.getString("currency_code"),
                result.getObject("primary_media_id", UUID.class),
                result.getString("primary_media_mime_type"),
                result.getString("primary_media_storage_key"));
    }
}
