package vn.covie.admin.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.covie.admin.api.dto.AdminUserSummaryResponse;

@Repository
public final class AdminUserReadRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public AdminUserReadRepository(NamedParameterJdbcTemplate jdbc){this.jdbc=jdbc;}
    public PageResult<AdminUserSummaryResponse> find(int page,int size,String status,String q,String sort){
        String where=" where u.deleted_at is null and (:status is null or u.account_status=:status) and (:q is null or lower(u.display_name) like :qLike or exists(select 1 from user_identities uiq where uiq.user_id=u.id and lower(uiq.email_normalized) like :qLike)) ";
        var p=new MapSqlParameterSource().addValue("status",blank(status)).addValue("q",blank(q)).addValue("qLike",blank(q)==null?null:"%"+q.strip().toLowerCase()+"%").addValue("limit",size).addValue("offset",page*size);
        long total=jdbc.queryForObject("select count(*) from users u"+where,p,Long.class);
        String order=switch(sort){case "CREATED_ASC"->" order by u.created_at asc,u.id asc";case "UPDATED_DESC"->" order by u.updated_at desc,u.id desc";default->" order by u.created_at desc,u.id desc";};
        String sql="""
                select u.id,u.display_name,u.account_status,u.created_at,u.updated_at,
                    (select min(ui.email_normalized) from user_identities ui where ui.user_id=u.id and ui.email_normalized is not null) email,
                    (select count(*) from auth_sessions s where s.user_id=u.id and s.revoked_at is null) active_session_count,
                    (select coalesce(string_agg(a.role,',' order by a.role),'') from system_role_assignments a where a.user_id=u.id) roles
                from users u
                """+where+order+" limit :limit offset :offset";
        return new PageResult<>(jdbc.query(sql,p,this::map),total);
    }
    public Optional<AdminUserSummaryResponse> findById(UUID id){
        var p=Map.of("id",id); String sql="""
                select u.id,u.display_name,u.account_status,u.created_at,u.updated_at,
                    (select min(ui.email_normalized) from user_identities ui where ui.user_id=u.id and ui.email_normalized is not null) email,
                    (select count(*) from auth_sessions s where s.user_id=u.id and s.revoked_at is null) active_session_count,
                    (select coalesce(string_agg(a.role,',' order by a.role),'') from system_role_assignments a where a.user_id=u.id) roles
                from users u where u.id=:id and u.deleted_at is null
                """;
        return jdbc.query(sql,p,this::map).stream().findFirst();
    }
    private AdminUserSummaryResponse map(ResultSet rs,int row) throws SQLException {String roles=rs.getString("roles");return new AdminUserSummaryResponse(rs.getObject("id",UUID.class),rs.getString("display_name"),rs.getString("email"),rs.getString("account_status"),roles==null||roles.isBlank()?List.of():Arrays.asList(roles.split(",")),rs.getLong("active_session_count"),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant());}
    private static String blank(String s){return s==null||s.isBlank()?null:s.strip();}
    public record PageResult<T>(List<T> items,long total) {}
}
