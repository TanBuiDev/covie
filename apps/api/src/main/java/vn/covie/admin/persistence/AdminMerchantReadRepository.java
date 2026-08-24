package vn.covie.admin.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.covie.admin.api.dto.AdminMerchantSummaryResponse;

@Repository
public final class AdminMerchantReadRepository {
    private final NamedParameterJdbcTemplate jdbc; public AdminMerchantReadRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public PageResult<AdminMerchantSummaryResponse> find(int page,int size,String status,String moderationStatus,String q,String sort){
        String where=" where m.deleted_at is null and (:status is null or m.merchant_status=:status) and (:moderation is null or m.moderation_status=:moderation) and (:q is null or lower(m.name) like :qLike or lower(m.slug) like :qLike) ";
        String normalized=q==null||q.isBlank()?null:q.strip().toLowerCase(); var p=new MapSqlParameterSource().addValue("status",blank(status)).addValue("moderation",blank(moderationStatus)).addValue("q",normalized).addValue("qLike",normalized==null?null:"%"+normalized+"%").addValue("limit",size).addValue("offset",page*size);
        long total=jdbc.queryForObject("select count(*) from merchant_profiles m"+where,p,Long.class);
        String order=switch(sort){case "CREATED_ASC"->" order by m.created_at asc,m.id asc";case "UPDATED_DESC"->" order by m.updated_at desc,m.id desc";default->" order by m.created_at desc,m.id desc";};
        return new PageResult<>(jdbc.query(select()+where+order+" limit :limit offset :offset",p,this::map),total);
    }
    public Optional<AdminMerchantSummaryResponse> findById(UUID id){return jdbc.query(select()+" where m.id=:id and m.deleted_at is null",Map.of("id",id),this::map).stream().findFirst();}
    private static String select(){return """
            select m.id,m.name,m.slug,m.merchant_status,m.moderation_status,m.created_at,m.updated_at,
              (select count(*) from merchant_memberships mm where mm.merchant_profile_id=m.id and mm.role='OWNER' and mm.membership_status='ACTIVE') owner_count,
              (select count(*) from merchant_memberships mm where mm.merchant_profile_id=m.id and mm.membership_status='ACTIVE') member_count,
              (select count(*) from merchant_locations ml where ml.merchant_profile_id=m.id and ml.deleted_at is null) location_count
            from merchant_profiles m
            """;}
    private AdminMerchantSummaryResponse map(ResultSet rs,int row)throws SQLException{return new AdminMerchantSummaryResponse(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("slug"),rs.getString("merchant_status"),rs.getString("moderation_status"),rs.getLong("owner_count"),rs.getLong("member_count"),rs.getLong("location_count"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());}
    private static String blank(String s){return s==null||s.isBlank()?null:s.strip();}
    public record PageResult<T>(List<T> items,long total) {}
}
