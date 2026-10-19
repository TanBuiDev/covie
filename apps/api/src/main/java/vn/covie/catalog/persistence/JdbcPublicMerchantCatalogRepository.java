package vn.covie.catalog.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Autowired;
import vn.covie.settings.application.SystemSettingResolver;
import vn.covie.settings.application.SystemSettingContext;
import vn.covie.shared.time.AppClock;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.api.dto.publicview.PublicMerchantCatalogCursor;
import vn.covie.catalog.model.ListingKind;

@Repository
public class JdbcPublicMerchantCatalogRepository implements PublicMerchantCatalogRepository {
    static final String PUBLIC_AVAILABILITY = """
            ci.listing_status = 'ACTIVE'
            and ci.moderation_status in ('NOT_REQUIRED', 'APPROVED')
            and ci.deleted_at is null
            and v.variant_status = 'ACTIVE'
            and v.deleted_at is null
            and lv.is_available
            and lv.deleted_at is null
            and ml.location_status = 'ACTIVE'
            and ml.deleted_at is null
            and ml.merchant_profile_id = ci.merchant_profile_id
            and (:billingEnforced = false or exists (
                select 1 from billing_entitlement_grants bge
                where bge.merchant_id = ci.merchant_profile_id and bge.revoked_at is null
                  and bge.valid_from <= cast(:billingNow as timestamptz)
                  and bge.valid_until > cast(:billingNow as timestamptz)
            ))
            """;

    private final NamedParameterJdbcTemplate jdbc;
    private final SystemSettingResolver settings;
    private final AppClock clock;

    public JdbcPublicMerchantCatalogRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc; this.settings = null; this.clock = null;
    }

    @Autowired
    public JdbcPublicMerchantCatalogRepository(NamedParameterJdbcTemplate jdbc,
            SystemSettingResolver settings, AppClock clock) {
        this.jdbc = jdbc; this.settings = settings; this.clock = clock;
    }

    private MapSqlParameterSource visibilityParameters(UUID merchantId) {
        return new MapSqlParameterSource()
            .addValue("merchantId", merchantId)
            .addValue("billingEnforced", settings != null &&
                settings.bool("billing.enforcement_enabled", SystemSettingContext.globalOnly()))
            .addValue("billingNow", (clock == null ? java.time.Instant.now() : clock.now())
                .atOffset(java.time.ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE);
    }

    @Override
    public List<CatalogItemType> findCatalogTypes(UUID merchantId) {
        String sql = """
                select distinct ci.item_type
                from catalog_items ci
                join variants v on v.catalog_item_id = ci.id
                join location_variants lv on lv.variant_id = v.id
                join merchant_locations ml on ml.id = lv.merchant_location_id
                where ci.merchant_profile_id = :merchantId
                  and %s
                order by ci.item_type
                """.formatted(PUBLIC_AVAILABILITY);
        return jdbc.query(sql, visibilityParameters(merchantId),
                (result, ignored) -> CatalogItemType.valueOf(result.getString("item_type")));
    }

    @Override
    public List<Row> findItems(UUID merchantId, PublicMerchantCatalogCursor cursor, int fetchLimit) {
        MapSqlParameterSource parameters = visibilityParameters(merchantId)
                .addValue("fetchLimit", fetchLimit);
        String cursorPredicate = "";
        if (cursor != null) {
            cursorPredicate = "and (public_item.created_at, public_item.item_id) < (:cursorCreatedAt, :cursorItemId)";
            parameters.addValue("cursorCreatedAt", cursor.createdAt().atOffset(ZoneOffset.UTC), Types.TIMESTAMP_WITH_TIMEZONE)
                    .addValue("cursorItemId", cursor.itemId());
        }
        String sql = """
                with public_item as (
                    select distinct on (ci.id)
                           ci.id as item_id, ci.item_type, ci.listing_kind, ci.name, ci.slug, ci.created_at,
                           lv.price, lv.currency_code
                    from catalog_items ci
                    join variants v on v.catalog_item_id = ci.id
                    join location_variants lv on lv.variant_id = v.id
                    join merchant_locations ml on ml.id = lv.merchant_location_id
                    where ci.merchant_profile_id = :merchantId
                      and %s
                    order by ci.id, lv.price asc, lv.id asc
                )
                select public_item.*,
                       media.id as primary_media_id,
                       media.mime_type as primary_media_mime_type,
                       media.storage_key as primary_media_storage_key
                from public_item
                left join lateral (
                    select ma.id, ma.mime_type, ma.storage_key
                    from catalog_item_media cim
                    join media_assets ma on ma.id = cim.media_asset_id
                    where cim.catalog_item_id = public_item.item_id
                      and cim.is_primary
                      and ma.media_status = 'READY'
                      and ma.deleted_at is null
                    order by cim.sort_order asc, ma.id asc
                    limit 1
                ) media on true
                where true %s
                order by public_item.created_at desc, public_item.item_id desc
                limit :fetchLimit
                """.formatted(PUBLIC_AVAILABILITY, cursorPredicate);
        return jdbc.query(sql, parameters, this::mapItem);
    }

    private Row mapItem(ResultSet result, int ignored) throws SQLException {
        return new Row(result.getObject("item_id", UUID.class),
                CatalogItemType.valueOf(result.getString("item_type")),
                ListingKind.valueOf(result.getString("listing_kind")), result.getString("name"),
                result.getString("slug"), result.getTimestamp("created_at").toInstant(), result.getLong("price"),
                result.getString("currency_code"), result.getObject("primary_media_id", UUID.class),
                result.getString("primary_media_mime_type"), result.getString("primary_media_storage_key"));
    }
}
