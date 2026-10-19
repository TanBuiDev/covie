package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.PullPolicy;
import org.testcontainers.utility.MountableFile;

@EnabledIfSystemProperty(named = "covie.test.catalog-video-probe", matches = "true")
class CatalogVideoProbeIntegrationTest {
    @Test void verifiesAndDecodesRealLocalVideosWithoutNetwork() throws Exception {
        String image = System.getProperty("covie.test.video-tool-image", "covie-video-tools:local-verification");
        try (var tools = new GenericContainer<>(image).withImagePullPolicy(PullPolicy.defaultPolicy())
                .withCreateContainerCmdModifier(command -> {
                    command.withEntrypoint("/bin/sh");
                    command.getHostConfig().withNetworkMode("none").withMemory(536_870_912L).withNanoCPUs(1_000_000_000L);
                }).withCommand("-c", "sleep 300").waitingFor(Wait.forSuccessfulCommand("true"))) {
            tools.start();
            execute(tools, List.of("mkdir", "-p", "/tmp/validation/classes/vn/covie/media/application", "/tmp/validation/lib", "/tmp/validation/media"));
            Path main = Path.of(CatalogVideoProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            Path tests = Path.of(CatalogVideoProbeIntegrationTest.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (String name : List.of("CatalogVideoProbe.class", "CatalogVideoProbe$Metadata.class", "CatalogVideoProbe$InvalidVideoException.class", "MediaProbeProcess.class", "MediaProbeProcess$Failed.class"))
                copy(tools, main.resolve("vn/covie/media/application/" + name), "/tmp/validation/classes/vn/covie/media/application/" + name);
            copy(tools, tests.resolve("vn/covie/media/application/CatalogVideoProbeIntegrationTest$Fixture.class"),
                    "/tmp/validation/classes/vn/covie/media/application/CatalogVideoProbeIntegrationTest$Fixture.class");
            copy(tools, Path.of(ObjectMapper.class.getProtectionDomain().getCodeSource().getLocation().toURI()), "/tmp/validation/lib/databind.jar");
            copy(tools, Path.of(JsonFactory.class.getProtectionDomain().getCodeSource().getLocation().toURI()), "/tmp/validation/lib/core.jar");
            copy(tools, Path.of(JsonProperty.class.getProtectionDomain().getCodeSource().getLocation().toURI()), "/tmp/validation/lib/annotations.jar");
            makeClip(tools, "valid.mp4", "libx264", "aac");
            makeClip(tools, "valid.webm", "libvpx-vp9", "libopus");
            makeClip(tools, "vp8.webm", "libvpx", "libvorbis");
            makeClip(tools, "unsupported.mp4", "mpeg4", "aac");
            execute(tools, List.of("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "color=c=blue:s=64x48:r=1",
                    "-t", "61", "-c:v", "libx264", "-threads", "1", "-pix_fmt", "yuv420p", "/tmp/validation/media/long.mp4"));
            execute(tools, List.of("sh", "-c", "head -c $(( $(stat -c%s /tmp/validation/media/valid.mp4) / 2 )) /tmp/validation/media/valid.mp4 > /tmp/validation/media/truncated.mp4; : > /tmp/validation/media/empty.mp4"));
            String output = execute(tools, List.of("java", "-cp", "/tmp/validation/classes:/tmp/validation/lib/*", Fixture.class.getName()));
            assertThat(output).contains("PASS real MP4/WebM probe/decode and invalid-file denials");
        }
    }

    private static void copy(GenericContainer<?> tools, Path source, String destination) {
        tools.copyFileToContainer(MountableFile.forHostPath(source), destination);
    }

    private static void makeClip(GenericContainer<?> tools, String name, String codec, String audio) throws Exception {
        var command = new ArrayList<>(List.of("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=64x48:rate=5",
                "-f", "lavfi", "-i", "anullsrc=sample_rate=48000:channel_layout=stereo", "-t", "1", "-c:v", codec,
                "-threads", "1", "-c:a", audio, "-pix_fmt", "yuv420p"));
        if (name.endsWith(".mp4")) command.addAll(List.of("-movflags", "+faststart"));
        command.add("/tmp/validation/media/" + name);
        execute(tools, command);
    }

    private static String execute(GenericContainer<?> tools, List<String> command) throws Exception {
        var result = tools.execInContainer(command.toArray(String[]::new));
        assertThat(result.getExitCode()).as("%s: %s", command.getFirst(), result.getStderr()).isZero();
        return result.getStdout();
    }

    public static class Fixture {
        public static void main(String[] arguments) throws Exception {
            var probe = new CatalogVideoProbe(Path.of("/usr/bin/ffprobe"), Path.of("/usr/bin/ffmpeg"), Duration.ofSeconds(20));
            valid(probe, "valid.mp4", "video/mp4", "h264");
            valid(probe, "valid.webm", "video/webm", "vp9");
            valid(probe, "vp8.webm", "video/webm", "vp8");
            rejected(probe, "unsupported.mp4", "video/mp4");
            rejected(probe, "long.mp4", "video/mp4");
            rejected(probe, "truncated.mp4", "video/mp4");
            rejected(probe, "empty.mp4", "video/mp4");
            rejected(probe, "valid.mp4", "video/webm");
            rejected(new CatalogVideoProbe(Path.of("/missing/ffprobe"), Path.of("/missing/ffmpeg"), Duration.ofSeconds(1)),
                    "valid.mp4", "video/mp4");
            System.out.println("PASS real MP4/WebM probe/decode and invalid-file denials");
        }

        private static void valid(CatalogVideoProbe probe, String file, String type, String codec) throws Exception {
            var metadata = probe.inspect(Path.of("/tmp/validation/media", file), type);
            if (metadata.width() != 64 || metadata.height() != 48 || !metadata.codec().equals(codec)
                    || metadata.durationMillis() < 1000 || metadata.durationMillis() > 2000) throw new AssertionError(file);
        }

        private static void rejected(CatalogVideoProbe probe, String file, String type) throws Exception {
            try { probe.inspect(Path.of("/tmp/validation/media", file), type); }
            catch (IOException expected) { return; }
            throw new AssertionError("Invalid video accepted: " + file);
        }
    }
}
