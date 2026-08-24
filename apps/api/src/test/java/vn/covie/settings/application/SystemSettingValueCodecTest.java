package vn.covie.settings.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class SystemSettingValueCodecTest {

    private final SystemSettingValueCodec codec = new SystemSettingValueCodec();

    @Test
    void refusesStringBooleanCoercion() {
        assertThatThrownBy(() -> codec.booleanValue("review.enabled", JsonNodeFactory.instance.textNode("true")))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).code())
                .isEqualTo(ErrorCode.SYSTEM_SETTING_VALUE_INVALID);
    }

    @Test
    void readsExactScalarValues() {
        assertThat(codec.booleanValue("review.enabled", JsonNodeFactory.instance.booleanNode(true))).isTrue();
        assertThat(codec.integerValue("bulk.max_items", JsonNodeFactory.instance.numberNode(100))).isEqualTo(100);
        assertThat(codec.enumValue("moderation.mode", JsonNodeFactory.instance.textNode("AUTO_PUBLISH"))).isEqualTo("AUTO_PUBLISH");
    }
}
