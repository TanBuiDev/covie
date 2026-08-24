package vn.covie.settings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import vn.covie.settings.model.SettingValueType;
import vn.covie.settings.model.SystemSettingEntity;
import vn.covie.settings.persistence.SystemSettingRepository;

class SystemSettingResolverTest {

    @Test
    void merchantBeatsCategoryAreaAndGlobal() {
        SystemSettingRepository repository = Mockito.mock(SystemSettingRepository.class);
        UUID merchant = UUID.randomUUID();
        UUID category = UUID.randomUUID();
        UUID area = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-24T00:00:00Z");
        when(repository.findBySettingKeyAndScopeTypeAndMerchantProfileId(any(), any(), any())).thenReturn(Optional.of(
                SystemSettingEntity.merchant(UUID.randomUUID(), "moderation.mode", SettingValueType.ENUM,
                        JsonNodeFactory.instance.textNode("HYBRID"), merchant, UUID.randomUUID(), now)));

        SystemSettingResolver resolver = new SystemSettingResolver(new SystemSettingRegistry(), repository, new SystemSettingValueCodec());

        assertThat(resolver.enumName("moderation.mode", new SystemSettingContext(merchant, category, area))).isEqualTo("HYBRID");
    }

    @Test
    void resolvesRegistryDefaultWhenNoRowExists() {
        SystemSettingRepository repository = Mockito.mock(SystemSettingRepository.class);
        when(repository.findBySettingKeyAndScopeType(any(), any())).thenReturn(Optional.empty());
        SystemSettingResolver resolver = new SystemSettingResolver(new SystemSettingRegistry(), repository, new SystemSettingValueCodec());

        assertThat(resolver.integer("bulk.max_items", SystemSettingContext.globalOnly())).isEqualTo(100);
    }
}
