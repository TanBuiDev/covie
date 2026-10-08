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
import vn.covie.catalog.api.dto.discovery.CatalogCursor;
import vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery;
import vn.covie.catalog.api.dto.discovery.CatalogSort;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.ListingKind;
import vn.covie.location.application.LocationContext;

@Repository
public class JdbcCatalogDiscoveryRepository implements CatalogDiscoveryRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public JdbcCatalogDiscoveryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Row> find(CatalogDiscoveryQuery query, LocationContext context, CatalogCursor cursor, Instant now) {
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("lat", context.point().getY())
                .addValue("lng", context.point().getX())
                .addValue("radiusMeters", query.radiusMeters())
                .addValue("pickup", Boolean.TRUE.equals(query.pickup()))
                .addValue("canServe", Boolean.TRUE.equals(query.canServe()))
                .addValue("openNow", Boolean.TRUE.equals(query.openNow()))
                .addValue("contextAreaId", context.locationAreaId(), Types.OTHER)
                .addValue("now", now.atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                .addValue("q", normalizedTerm(query.q()), Types.VARCHAR)
                .addValue("fetchLimit", query.limit() + 1);
        parameters.addValue("shippingMode", query.shipping() == null ? null : query.shipping().name(), Types.VARCHAR);

        StringBuilder filters = new StringBuilder();
        if (query.type() != null) {
            filters.append(" and ci.item_type = :type");
            parameters.addValue("type", query.type().name());
        }
        if (query.listingKind() != null) {
            filters.append(" and ci.listing_kind = :listingKind");
            parameters.addValue("listingKind", query.listingKind().name());
        }
        if (query.categoryId() != null) {
            filters.append(' ');
            filters.append("""
                and ci.primary_category_id in (
                    with recursive selected_categories(id) as (
                        select id from categories
                        where id = :categoryId and status = 'ACTIVE' and deleted_at is null
                        union
                        select child.id from categories child
                        join selected_categories parent on child.parent_id = parent.id
                        where child.status = 'ACTIVE' and child.deleted_at is null
                    ) select id from selected_categories
                )
                """);
            parameters.addValue("categoryId", query.categoryId());
        }
        if (query.minPrice() != null) {
            filters.append(" and rp.min_price >= :minPrice");
            parameters.addValue("minPrice", query.minPrice());
        }
        if (query.maxPrice() != null) {
            filters.append(" and rp.min_price <= :maxPrice");
            parameters.addValue("maxPrice", query.maxPrice());
        }
        appendCursorPredicate(filters, parameters, query.sort(), cursor);

        String sql = """
                with candidate_locations as (
                    select ml.id, ml.merchant_profile_id, ml.name, ml.address_text, ml.timezone, ml.currency_code,
                           ml.geo_point, ml.pickup_enabled, ml.seller_delivery_enabled, ml.service_enabled,
                           st_distance(ml.geo_point, st_setsrid(st_makepoint(:lng, :lat), 4326)::geography)::bigint as distance_meters
                    from merchant_locations ml
                    join merchant_profiles mp on mp.id = ml.merchant_profile_id
                    where ml.location_status = 'ACTIVE' and ml.deleted_at is null
                      and mp.merchant_status = 'ACTIVE' and mp.moderation_status in ('NOT_REQUIRED', 'APPROVED') and mp.deleted_at is null
                      and st_dwithin(ml.geo_point, st_setsrid(st_makepoint(:lng, :lat), 4326)::geography, :radiusMeters)
                      and (:pickup = false or ml.pickup_enabled)
                      and (:shippingMode is null or exists (
                            select 1 from shipping_policies sp
                            where sp.merchant_location_id = ml.id and sp.policy_status = 'ACTIVE'
                              and sp.mode = :shippingMode
                          ))
                      and (:openNow = false or (
                            not exists (
                                select 1 from merchant_location_hour_exceptions e
                                where e.merchant_location_id = ml.id
                                  and e.operation_type = 'SHOP'
                                  and e.local_date = timezone(ml.timezone, cast(:now as timestamptz))::date
                            )
                            and exists (
                                select 1 from merchant_location_hours h
                                where h.merchant_location_id = ml.id and h.operation_type = 'SHOP'
                                  and h.day_of_week = extract(isodow from timezone(ml.timezone, cast(:now as timestamptz)))
                                  and not h.is_closed
                                  and timezone(ml.timezone, cast(:now as timestamptz))::time >= h.open_time
                                  and timezone(ml.timezone, cast(:now as timestamptz))::time < h.close_time
                            )
                          ) or (
                            not exists (
                                select 1 from merchant_location_hour_exceptions e
                                where e.merchant_location_id = ml.id and e.operation_type = 'SHOP'
                                  and e.local_date = timezone(ml.timezone, cast(:now as timestamptz))::date
                                  and e.is_closed
                            )
                            and exists (
                                select 1 from merchant_location_hour_exceptions e
                                where e.merchant_location_id = ml.id and e.operation_type = 'SHOP'
                                  and e.local_date = timezone(ml.timezone, cast(:now as timestamptz))::date
                                  and not e.is_closed
                                  and timezone(ml.timezone, cast(:now as timestamptz))::time >= e.open_time
                                  and timezone(ml.timezone, cast(:now as timestamptz))::time < e.close_time
                            )
                          ))
                ), resolved as (
                    select distinct on (ci.id) ci.id as item_id, cl.id as location_id, cl.distance_meters, cl.currency_code
                    from catalog_items ci
                    join variants v on v.catalog_item_id = ci.id and v.variant_status = 'ACTIVE' and v.deleted_at is null
                    join location_variants lv on lv.variant_id = v.id and lv.deleted_at is null and lv.is_available
                    join candidate_locations cl on cl.id = lv.merchant_location_id
                        and cl.merchant_profile_id = ci.merchant_profile_id
                    where ci.listing_status = 'ACTIVE'
                      and ci.moderation_status in ('NOT_REQUIRED', 'APPROVED') and ci.deleted_at is null
                      and (:canServe = false or (
                            (ci.item_type = 'PRODUCT' and (cl.pickup_enabled or (
                                cl.seller_delivery_enabled and exists (
                                    select 1 from delivery_coverages dc
                                    where dc.merchant_location_id = cl.id and dc.coverage_type = 'PRODUCT_DELIVERY'
                                      and dc.coverage_status = 'ACTIVE' and (
                                        (dc.radius_enabled and st_dwithin(cl.geo_point,
                                            st_setsrid(st_makepoint(:lng, :lat), 4326)::geography, dc.radius_meters))
                                        or (dc.area_enabled and cast(:contextAreaId as uuid) is not null and exists (
                                            select 1 from delivery_coverage_areas dca
                                            where dca.delivery_coverage_id = dc.id and dca.location_area_id = cast(:contextAreaId as uuid)
                                        ))
                                      )
                                )
                            )))
                            or (ci.item_type = 'SERVICE' and cl.service_enabled and exists (
                                select 1 from delivery_coverages dc
                                where dc.merchant_location_id = cl.id and dc.coverage_type = 'SERVICE_AREA'
                                  and dc.coverage_status = 'ACTIVE' and (
                                    (dc.radius_enabled and st_dwithin(cl.geo_point,
                                        st_setsrid(st_makepoint(:lng, :lat), 4326)::geography, dc.radius_meters))
                                    or (dc.area_enabled and cast(:contextAreaId as uuid) is not null and exists (
                                        select 1 from delivery_coverage_areas dca
                                        where dca.delivery_coverage_id = dc.id and dca.location_area_id = cast(:contextAreaId as uuid)
                                    ))
                                  )
                            ))
                          ))
                    order by ci.id, cl.distance_meters, cl.id
                ), resolved_pricing as (
                    select r.item_id, r.location_id, r.distance_meters, min(lv.price)::bigint as min_price,
                           r.currency_code
                    from resolved r
                    join variants v on v.catalog_item_id = r.item_id and v.variant_status = 'ACTIVE' and v.deleted_at is null
                    join location_variants lv on lv.variant_id = v.id and lv.merchant_location_id = r.location_id
                        and lv.is_available and lv.deleted_at is null
                    group by r.item_id, r.location_id, r.distance_meters, r.currency_code
                ), query_term as (
                    select case when :q is null or btrim(:q) = '' then null
                        else websearch_to_tsquery('simple', unaccent(cast(:q as text))) end as query
                )
                select ci.id as item_id, ci.item_type, ci.listing_kind, ci.name, ci.slug, ci.created_at,
                       mp.id as merchant_id, mp.name as merchant_name, mp.slug as merchant_slug,
                       cl.id as location_id, cl.name as location_name, cl.address_text, cl.timezone,
                       rp.distance_meters, rp.min_price, rp.currency_code,
                       media.id as primary_media_id, media.mime_type as primary_media_mime_type,
                       media.storage_key as primary_media_storage_key,
                       case when qt.query is null then null
                            else round(ts_rank_cd(ci.search_vector, qt.query)::numeric, 8) end as rank_key
                from resolved_pricing rp
                join catalog_items ci on ci.id = rp.item_id
                join merchant_profiles mp on mp.id = ci.merchant_profile_id
                    and mp.merchant_status = 'ACTIVE' and mp.moderation_status in ('NOT_REQUIRED', 'APPROVED') and mp.deleted_at is null
                join candidate_locations cl on cl.id = rp.location_id
                cross join query_term qt
                left join lateral (
                    select ma.id, ma.mime_type, ma.storage_key
                    from catalog_item_media cim join media_assets ma on ma.id = cim.media_asset_id
                    where cim.catalog_item_id = ci.id and cim.is_primary and ma.media_status = 'READY' and ma.deleted_at is null
                    order by cim.sort_order, ma.id limit 1
                ) media on true
                where (qt.query is null or ci.search_vector @@ qt.query)
                %s
                %s
                limit :fetchLimit
                """.formatted(filters, orderBy(query.sort()));
        return jdbc.query(sql, parameters, this::map);
    }

    private static void appendCursorPredicate(StringBuilder filters, MapSqlParameterSource parameters,
            CatalogSort sort, CatalogCursor cursor) {
        if (cursor == null) return;
        parameters.addValue("cursorItemId", cursor.itemId());
        switch (sort) {
            case DISTANCE -> {
                filters.append(" and (rp.distance_meters, ci.id) > (:cursorDistance, :cursorItemId)");
                parameters.addValue("cursorDistance", cursor.distanceMeters());
            }
            case PRICE_ASC -> {
                filters.append(" and (rp.min_price, ci.id) > (:cursorPrice, :cursorItemId)");
                parameters.addValue("cursorPrice", cursor.price());
            }
            case PRICE_DESC -> {
                filters.append(" and (rp.min_price, ci.id) < (:cursorPrice, :cursorItemId)");
                parameters.addValue("cursorPrice", cursor.price());
            }
            case NEWEST -> {
                filters.append(" and (ci.created_at, ci.id) < (:cursorCreatedAt, :cursorItemId)");
                parameters.addValue("cursorCreatedAt", cursor.createdAt().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
            }
            case RELEVANCE -> {
                String rank = "round(ts_rank_cd(ci.search_vector, qt.query)::numeric, 8)";
                filters.append(" and (" + rank + " < :cursorRank or (" + rank + " = :cursorRank and "
                        + "(rp.distance_meters > :cursorDistance or (rp.distance_meters = :cursorDistance and ci.id > :cursorItemId))))");
                parameters.addValue("cursorRank", cursor.rank());
                parameters.addValue("cursorDistance", cursor.distanceMeters());
            }
        }
    }

    private static String orderBy(CatalogSort sort) {
        return switch (sort) {
            case RELEVANCE -> "order by rank_key desc, rp.distance_meters asc, ci.id asc";
            case DISTANCE -> "order by rp.distance_meters asc, ci.id asc";
            case PRICE_ASC -> "order by rp.min_price asc, ci.id asc";
            case PRICE_DESC -> "order by rp.min_price desc, ci.id desc";
            case NEWEST -> "order by ci.created_at desc, ci.id desc";
        };
    }

    private static String normalizedTerm(String q) {
        return q == null || q.isBlank() ? null : q.trim();
    }

    private Row map(ResultSet result, int ignored) throws SQLException {
        return new Row(result.getObject("item_id", UUID.class), CatalogItemType.valueOf(result.getString("item_type")),
                ListingKind.valueOf(result.getString("listing_kind")), result.getString("name"), result.getString("slug"),
                result.getTimestamp("created_at").toInstant(), result.getObject("merchant_id", UUID.class),
                result.getString("merchant_name"), result.getString("merchant_slug"), result.getObject("location_id", UUID.class),
                result.getString("location_name"), result.getString("address_text"), result.getString("timezone"),
                result.getLong("distance_meters"), result.getLong("min_price"), result.getString("currency_code"),
                result.getObject("primary_media_id", UUID.class), result.getString("primary_media_mime_type"),
                result.getString("primary_media_storage_key"), result.getBigDecimal("rank_key"));
    }
}
