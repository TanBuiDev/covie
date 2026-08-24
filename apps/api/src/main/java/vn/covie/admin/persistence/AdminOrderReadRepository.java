package vn.covie.admin.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.covie.admin.api.dto.AdminOrderSummaryResponse;
import vn.covie.orders.model.OrderStatus;
import vn.covie.orders.model.OrderType;

@Repository
public class AdminOrderReadRepository {
    private final NamedParameterJdbcTemplate jdbc; public AdminOrderReadRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public PageResult<AdminOrderSummaryResponse> find(int page,int size,String status,String type,UUID merchantId,UUID buyerId,String orderCode,String sort){
        String where=" where (:status is null or o.status=:status) and (:type is null or o.order_type=:type) and (:merchantId is null or o.merchant_profile_id=:merchantId) and (:buyerId is null or o.buyer_user_id=:buyerId) and (:orderCode is null or o.order_code=:orderCode) ";
        var p=new MapSqlParameterSource().addValue("status",blank(status)).addValue("type",blank(type)).addValue("merchantId",merchantId).addValue("buyerId",buyerId).addValue("orderCode",blank(orderCode)).addValue("limit",size).addValue("offset",page*size);
        long total=jdbc.queryForObject("select count(*) from orders o"+where,p,Long.class);
        String order=switch(sort){case "CREATED_ASC"->" order by o.created_at asc,o.id asc";case "UPDATED_DESC"->" order by o.updated_at desc,o.id desc";default->" order by o.created_at desc,o.id desc";};
        String sql="select o.id,o.order_code,o.order_type,o.status,o.buyer_user_id,o.merchant_profile_id,o.merchant_location_id,o.known_total,o.currency_code,o.created_at,o.updated_at from orders o"+where+order+" limit :limit offset :offset";
        return new PageResult<>(jdbc.query(sql,p,this::map),total);
    }
    private AdminOrderSummaryResponse map(ResultSet rs,int row)throws SQLException{return new AdminOrderSummaryResponse(rs.getObject("id",UUID.class),rs.getString("order_code"),OrderType.valueOf(rs.getString("order_type")),OrderStatus.valueOf(rs.getString("status")),rs.getObject("buyer_user_id",UUID.class),rs.getObject("merchant_profile_id",UUID.class),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());}
    private static String blank(String s){return s==null||s.isBlank()?null:s.strip();}
    public record PageResult<T>(List<T> items,long total) {}
}
