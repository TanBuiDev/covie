package vn.covie.needs.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NeedPersistenceIntegrationTest {

    @Test
    void exposesNeedPersistenceModelAndRepositories() {
        assertThat(load("vn.covie.needs.model.NeedRequestEntity")).isNotNull();
        assertThat(load("vn.covie.needs.model.NeedResponseEntity")).isNotNull();
        assertThat(load("vn.covie.needs.model.NeedResponseItemEntity")).isNotNull();
        assertThat(load("vn.covie.needs.persistence.NeedRequestRepository")).isNotNull();
        assertThat(load("vn.covie.needs.persistence.NeedResponseRepository")).isNotNull();
        assertThat(load("vn.covie.needs.persistence.NeedResponseItemRepository")).isNotNull();
    }

    private Class<?> load(String typeName) {
        try {
            return Class.forName(typeName);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
