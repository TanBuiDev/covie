package vn.covie.catalog.application;

import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CatalogSearchDocumentService {
    private final NamedParameterJdbcTemplate jdbc;
    public CatalogSearchDocumentService(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void rebuild(UUID itemId) {
        jdbc.update("""
            update catalog_items ci set search_vector =
                  setweight(to_tsvector('simple', unaccent(coalesce(ci.name, ''))), 'A')
               || setweight(to_tsvector('simple', unaccent(coalesce(ci.description, ''))), 'B')
               || setweight(to_tsvector('simple', unaccent(coalesce(mp.name, ''))), 'B')
               || setweight(to_tsvector('simple', unaccent(coalesce(c.name, ''))), 'B')
               || setweight(to_tsvector('simple', unaccent(coalesce((select string_agg(t.name, ' ') from catalog_item_tags cit join tags t on t.id = cit.tag_id where cit.catalog_item_id = ci.id), ''))), 'C')
            from merchant_profiles mp, categories c
            where ci.id = :itemId and mp.id = ci.merchant_profile_id and c.id = ci.primary_category_id
            """, Map.of("itemId", itemId));
    }
}
