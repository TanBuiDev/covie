package vn.covie.needs.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.covie.needs.model.NeedType;

@Repository
public class CategoryHierarchyRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public CategoryHierarchyRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    public boolean isSameOrDescendant(UUID requestedCategoryId, UUID candidateCategoryId) {
        String sql = """
                with recursive subtree as (
                    select id, parent_id from categories
                    where id = :requestedCategoryId and status = 'ACTIVE' and deleted_at is null
                    union all
                    select c.id, c.parent_id from categories c join subtree s on c.parent_id = s.id
                    where c.status = 'ACTIVE' and c.deleted_at is null
                ) select exists(select 1 from subtree where id = :candidateCategoryId)
                """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("requestedCategoryId", requestedCategoryId)
                .addValue("candidateCategoryId", candidateCategoryId), Boolean.class));
    }

    public boolean supportsNeedType(UUID categoryId, NeedType needType) {
        String sql = """
                select exists(select 1 from categories
                where id = :categoryId and status = 'ACTIVE' and deleted_at is null
                and catalog_type in ('BOTH', :catalogType))
                """;
        return Boolean.TRUE.equals(jdbc.queryForObject(sql, new MapSqlParameterSource()
                .addValue("categoryId", categoryId).addValue("catalogType", needType.name()), Boolean.class));
    }
}
