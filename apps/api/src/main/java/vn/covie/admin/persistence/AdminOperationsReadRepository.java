package vn.covie.admin.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.covie.admin.api.dto.AdminListingSummaryResponse;
import vn.covie.admin.api.dto.AdminNeedSummaryResponse;
import vn.covie.admin.api.dto.AdminShopLocationResponse;
import vn.covie.catalog.model.CatalogItemType;
import vn.covie.catalog.model.CatalogModerationStatus;
import vn.covie.catalog.model.ListingKind;
import vn.covie.catalog.model.ListingStatus;
import vn.covie.merchants.model.MerchantLocationStatus;
import vn.covie.merchants.model.MerchantModerationStatus;
import vn.covie.merchants.model.MerchantStatus;
import vn.covie.needs.model.NeedStatus;
import vn.covie.needs.model.NeedType;

@Repository
public class AdminOperationsReadRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public AdminOperationsReadRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResult<AdminShopLocationResponse> shops(
            int page, int size, String merchantStatus, String locationStatus, String q, String sort) {
        String where = """
                where m.deleted_at is null
                  and (cast(:merchantStatus as varchar) is null or m.merchant_status = :merchantStatus)
                  and (cast(:locationStatus as varchar) is null or ml.location_status = :locationStatus)
                  and (cast(:q as varchar) is null or lower(m.name) like :qLike or lower(coalesce(ml.name, '')) like :qLike
                       or lower(coalesce(ml.address_text, '')) like :qLike)
                """;
        var params = params(page, size, q).addValue("merchantStatus", blank(merchantStatus))
                .addValue("locationStatus", blank(locationStatus));
        String from = " from merchant_profiles m left join merchant_locations ml on ml.merchant_profile_id=m.id and ml.deleted_at is null ";
        long total = jdbc.queryForObject("select count(*)" + from + where, params, Long.class);
        String select = """
                select m.id merchant_id,m.name merchant_name,m.merchant_status,m.moderation_status,
                       ml.id location_id,ml.name location_name,ml.address_text,ml.location_status,
                       coalesce(ml.pickup_enabled,false) pickup_enabled,
                       coalesce(ml.seller_delivery_enabled,false) seller_delivery_enabled,
                       coalesce(ml.service_enabled,false) service_enabled,
                       coalesce(ml.updated_at,m.updated_at) updated_at
                """;
        String order = switch (sort) {
            case "CREATED_ASC" -> " order by m.created_at asc,m.id asc,ml.id asc";
            case "UPDATED_DESC" -> " order by coalesce(ml.updated_at,m.updated_at) desc,m.id desc,ml.id desc";
            default -> " order by m.created_at desc,m.id desc,ml.id desc";
        };
        return new PageResult<>(jdbc.query(select + from + where + order + " limit :limit offset :offset",
                params, this::shop), total);
    }

    public PageResult<AdminListingSummaryResponse> listings(
            int page, int size, String status, String moderation, String type, String q, String sort) {
        String where = """
                where i.deleted_at is null
                  and (cast(:status as varchar) is null or i.listing_status = :status)
                  and (cast(:moderation as varchar) is null or i.moderation_status = :moderation)
                  and (cast(:type as varchar) is null or i.item_type = :type)
                  and (cast(:q as varchar) is null or lower(i.name) like :qLike or lower(m.name) like :qLike)
                """;
        var params = params(page, size, q).addValue("status", blank(status))
                .addValue("moderation", blank(moderation)).addValue("type", blank(type));
        String from = " from catalog_items i join merchant_profiles m on m.id=i.merchant_profile_id ";
        long total = jdbc.queryForObject("select count(*)" + from + where, params, Long.class);
        String select = """
                select i.id,i.merchant_profile_id,m.name merchant_name,i.item_type,i.listing_kind,i.name,
                       i.listing_status,i.moderation_status,i.created_at,i.updated_at,
                       (select mc.id from moderation_cases mc
                        where mc.target_catalog_item_id=i.id and mc.case_status='OPEN'
                        order by mc.opened_at desc,mc.id desc limit 1) moderation_case_id
                """;
        String order = order(sort, "i");
        return new PageResult<>(jdbc.query(select + from + where + order + " limit :limit offset :offset",
                params, this::listing), total);
    }

    public PageResult<AdminNeedSummaryResponse> needs(
            int page, int size, String status, String type, String q, String sort) {
        String where = """
                where (cast(:status as varchar) is null or n.request_status = :status)
                  and (cast(:type as varchar) is null or n.need_type = :type)
                  and (cast(:q as varchar) is null or lower(n.title) like :qLike or lower(n.query_text) like :qLike)
                """;
        var params = params(page, size, q).addValue("status", blank(status)).addValue("type", blank(type));
        String from = " from need_requests n join categories c on c.id=n.category_id ";
        long total = jdbc.queryForObject("select count(*)" + from + where, params, Long.class);
        String select = """
                select n.id,n.need_type,n.title,n.category_id,c.name category_name,n.request_status,
                       n.search_radius_meters,n.expires_at,n.created_at,n.updated_at,
                       (select count(*) from need_responses nr where nr.need_request_id=n.id) response_count
                """;
        String order = order(sort, "n");
        return new PageResult<>(jdbc.query(select + from + where + order + " limit :limit offset :offset",
                params, this::need), total);
    }

    private AdminShopLocationResponse shop(ResultSet rs, int row) throws SQLException {
        String locationStatus = rs.getString("location_status");
        return new AdminShopLocationResponse(rs.getObject("merchant_id", UUID.class), rs.getString("merchant_name"),
                MerchantStatus.valueOf(rs.getString("merchant_status")),
                MerchantModerationStatus.valueOf(rs.getString("moderation_status")),
                rs.getObject("location_id", UUID.class), rs.getString("location_name"), rs.getString("address_text"),
                locationStatus == null ? null : MerchantLocationStatus.valueOf(locationStatus), rs.getBoolean("pickup_enabled"),
                rs.getBoolean("seller_delivery_enabled"), rs.getBoolean("service_enabled"),
                rs.getTimestamp("updated_at").toInstant());
    }

    private AdminListingSummaryResponse listing(ResultSet rs, int row) throws SQLException {
        return new AdminListingSummaryResponse(rs.getObject("id", UUID.class),
                rs.getObject("merchant_profile_id", UUID.class), rs.getString("merchant_name"),
                CatalogItemType.valueOf(rs.getString("item_type")), ListingKind.valueOf(rs.getString("listing_kind")),
                rs.getString("name"), ListingStatus.valueOf(rs.getString("listing_status")),
                CatalogModerationStatus.valueOf(rs.getString("moderation_status")),
                rs.getObject("moderation_case_id", UUID.class), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private AdminNeedSummaryResponse need(ResultSet rs, int row) throws SQLException {
        return new AdminNeedSummaryResponse(rs.getObject("id", UUID.class), NeedType.valueOf(rs.getString("need_type")),
                rs.getString("title"), rs.getObject("category_id", UUID.class), rs.getString("category_name"),
                NeedStatus.valueOf(rs.getString("request_status")), rs.getInt("search_radius_meters"),
                rs.getLong("response_count"), rs.getTimestamp("expires_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant());
    }

    private static MapSqlParameterSource params(int page, int size, String q) {
        String normalized = blank(q);
        if (normalized != null) normalized = normalized.toLowerCase();
        return new MapSqlParameterSource().addValue("q", normalized)
                .addValue("qLike", normalized == null ? null : "%" + normalized + "%")
                .addValue("limit", size).addValue("offset", page * size);
    }

    private static String order(String sort, String alias) {
        return switch (sort) {
            case "CREATED_ASC" -> " order by " + alias + ".created_at asc," + alias + ".id asc";
            case "UPDATED_DESC" -> " order by " + alias + ".updated_at desc," + alias + ".id desc";
            default -> " order by " + alias + ".created_at desc," + alias + ".id desc";
        };
    }

    private static String blank(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public record PageResult<T>(List<T> items, long total) {}
}
