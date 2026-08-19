package vn.covie.shared.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

class VersionPreconditionTest {

    @Test
    void parsesQuotedVersionEtag() {
        assertThat(VersionPrecondition.parseRequired("\"v12\"")).isEqualTo(12L);
    }

    @Test
    void formatsVersionEtag() {
        assertThat(VersionPrecondition.format(12L)).isEqualTo("\"v12\"");
    }

    @Test
    void rejectsMissingIfMatch() {
        assertThatThrownBy(() -> VersionPrecondition.parseRequired(null))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo(ErrorCode.IF_MATCH_REQUIRED));
    }

    @Test
    void rejectsMalformedIfMatch() {
        assertThatThrownBy(() -> VersionPrecondition.parseRequired("v12"))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo(ErrorCode.INVALID_IF_MATCH));
    }

    @Test
    void rejectsAnIfMatchVersionThatOverflowsLong() {
        assertThatThrownBy(() -> VersionPrecondition.parseRequired("\"v9223372036854775808\""))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> assertThat(((ApiException) ex).code())
                        .isEqualTo(ErrorCode.INVALID_IF_MATCH));
    }

    @Test
    void rejectsAStaleVersion() {
        assertThatThrownBy(() -> VersionPrecondition.require(11L, 12L))
                .isInstanceOf(ApiException.class)
                .satisfies(ex -> {
                    ApiException apiException = (ApiException) ex;
                    assertThat(apiException.code()).isEqualTo(ErrorCode.RESOURCE_VERSION_CONFLICT);
                    assertThat(apiException.details())
                            .containsEntry("expectedVersion", 11L)
                            .containsEntry("currentVersion", 12L);
                });
    }
}
