package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import org.junit.jupiter.api.Test;

class CatalogVideoProbeMetadataTest {
    @Test void readsSupportedVideoMetadataAndKeepsDurationPrecision() throws Exception {
        var metadata = CatalogVideoProbe.parseMetadata(json("h264", "1.0001", "mov,mp4,m4a,3gp,3g2,mj2", 64), "video/mp4");
        assertThat(metadata.width()).isEqualTo(64);
        assertThat(metadata.height()).isEqualTo(48);
        assertThat(metadata.codec()).isEqualTo("h264");
        assertThat(metadata.durationMillis()).isEqualTo(1001);
    }

    @Test void rejectsUnsupportedCodecAndMismatchedContainer() {
        assertThatThrownBy(() -> CatalogVideoProbe.parseMetadata(json("mpeg4", "1", "mov,mp4", 64), "video/mp4"))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> CatalogVideoProbe.parseMetadata(json("vp9", "1", "matroska,webm", 64), "video/mp4"))
                .isInstanceOf(IOException.class);
    }

    @Test void rejectsMissingOrUntrustworthyMetadata() {
        assertThatThrownBy(() -> CatalogVideoProbe.parseMetadata("{}", "video/mp4")).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> CatalogVideoProbe.parseMetadata(json("h264", "NaN", "mov,mp4", 64), "video/mp4"))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> CatalogVideoProbe.parseMetadata(json("h264", "1", "mov,mp4", 0), "video/mp4"))
                .isInstanceOf(IOException.class);
    }

    @Test void enforcesApprovedDurationOnlyOnProbeMetadata() throws Exception {
        assertThat(CatalogVideoProbe.parseMetadata(json("vp9", "60", "matroska,webm", 64), "video/webm").durationMillis())
                .isEqualTo(60_000);
        assertThatThrownBy(() -> CatalogVideoProbe.parseMetadata(json("vp9", "60.0001", "matroska,webm", 64), "video/webm"))
                .isInstanceOf(IOException.class);
    }

    private static String json(String codec, String duration, String format, int width) {
        return """
                {"streams":[{"codec_type":"video","codec_name":"%s","width":%d,"height":48}],
                 "format":{"format_name":"%s","duration":"%s"}}
                """.formatted(codec, width, format, duration);
    }
}
