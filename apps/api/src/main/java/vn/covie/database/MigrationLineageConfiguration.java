package vn.covie.database;

import java.sql.SQLException;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MigrationLineageConfiguration implements FlywayConfigurationCustomizer {
    @Override
    public void customize(FluentConfiguration configuration) {
        if (!configuration.isValidateOnMigrate() || configuration.isBaselineOnMigrate()) {
            throw new IllegalStateException("Migration lineage requires validation and forbids automatic baseline");
        }
        try (var connection = configuration.getDataSource().getConnection()) {
            String schema = configuration.getDefaultSchema();
            if (schema == null && configuration.getSchemas().length > 0) schema = configuration.getSchemas()[0];
            if (schema == null) schema = connection.getSchema();
            if (schema == null || schema.isBlank()) throw new IllegalStateException("Migration history schema is ambiguous");
            String relation = quote(schema) + "." + quote(configuration.getTable());
            try (var existence = connection.prepareStatement("select to_regclass(?)")) {
                existence.setString(1, relation);
                try (var result = existence.executeQuery()) {
                    if (!result.next()) throw new IllegalStateException("Cannot determine migration history");
                    if (result.getString(1) == null) return;
                }
            }
            boolean social = false;
            boolean hasFoundation = false;
            boolean hasLaterMigration = false;
            try (var statement = connection.prepareStatement("select version,script,success from " + relation);
                    var result = statement.executeQuery()) {
                while (result.next()) {
                    if (!result.getBoolean("success")) throw new IllegalStateException("Failed migration history requires operator review");
                    String version = result.getString("version");
                    if (version == null) continue;
                    var parsed = MigrationVersion.fromVersion(version);
                    if (parsed.compareTo(MigrationVersion.fromVersion("020")) > 0) hasLaterMigration = true;
                    if (parsed.compareTo(MigrationVersion.fromVersion("020")) != 0) continue;
                    if (hasFoundation) throw new IllegalStateException("Ambiguous V020 migration history");
                    hasFoundation = true;
                    String script = result.getString("script");
                    if ("V020__catalog_social_caption.sql".equals(script)) social = true;
                    else if (!"V020__seller_billing_foundation.sql".equals(script)) {
                        throw new IllegalStateException("Unknown V020 migration lineage");
                    }
                }
            }
            if (hasLaterMigration && !hasFoundation) throw new IllegalStateException("Incomplete migration lineage history");
            if (social) configuration.locations("classpath:db/migration-social", "classpath:db/migration/future");
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot safely determine migration lineage", exception);
        }
    }

    private String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
