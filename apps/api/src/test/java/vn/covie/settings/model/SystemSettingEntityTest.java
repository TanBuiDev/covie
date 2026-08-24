package vn.covie.settings.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SystemSettingEntityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void replacingValueRetainsImmutableSettingIdentity() throws Exception {
        UUID actor = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-08-24T00:00:00Z");
        SystemSettingEntity setting = SystemSettingEntity.global(
                UUID.randomUUID(), "review.enabled", SettingValueType.BOOLEAN,
                objectMapper.readTree("true"), actor, createdAt);

        JsonNode replacement = objectMapper.readTree("false");
        setting.replaceValue(replacement, UUID.randomUUID(), createdAt.plusSeconds(1));

        assertThat(setting.getSettingKey()).isEqualTo("review.enabled");
        assertThat(setting.getValueType()).isEqualTo(SettingValueType.BOOLEAN);
        assertThat(setting.getScopeType()).isEqualTo(SettingScopeType.GLOBAL);
        assertThat(setting.getValueJson()).isEqualTo(replacement);
        assertThat(setting.getUpdatedAt()).isEqualTo(createdAt.plusSeconds(1));
    }
}
