package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class MediaProbeProcessTest {
    @Test void readsMachineOutputFromActualProcess() throws Exception {
        String output = MediaProbeProcess.run(command("json"), Duration.ofSeconds(5));
        String options = System.getenv("JAVA_TOOL_OPTIONS");
        if (options != null && !options.isBlank()) {
            String diagnostic = "Picked up JAVA_TOOL_OPTIONS: " + options + "\n";
            assertThat(output).startsWith(diagnostic);
            output = output.substring(diagnostic.length());
        }
        assertThat(output).isEqualTo("{\"format\":{\"duration\":\"1.0\"}}");
    }

    @Test void rejectsNonzeroExitWithoutExposingProbeOutput() {
        assertThatThrownBy(() -> MediaProbeProcess.run(command("fail"), Duration.ofSeconds(5)))
                .isInstanceOf(IOException.class).hasMessage("Media probe process failed.")
                .hasMessageNotContaining("private output");
    }

    @Test void terminatesProcessAtDeadline() {
        assertThatThrownBy(() -> MediaProbeProcess.run(command("wait"), Duration.ofMillis(100)))
                .isInstanceOf(IOException.class).hasMessage("Media probe process timed out.");
    }

    @Test void rejectsOversizedOutput() {
        assertThatThrownBy(() -> MediaProbeProcess.run(command("large"), Duration.ofSeconds(5)))
                .isInstanceOf(IOException.class).hasMessage("Media probe output exceeds limit.");
    }

    private static List<String> command(String mode) {
        boolean windows = System.getProperty("os.name").startsWith("Windows");
        return List.of(Path.of(System.getProperty("java.home"), "bin", windows ? "java.exe" : "java").toString(),
                "-cp", System.getProperty("java.class.path"), Child.class.getName(), mode);
    }

    public static class Child {
        public static void main(String[] arguments) throws Exception {
            switch (arguments[0]) {
                case "json" -> System.out.print("{\"format\":{\"duration\":\"1.0\"}}");
                case "fail" -> { System.out.print("private output"); System.exit(2); }
                case "wait" -> Thread.sleep(30_000);
                case "large" -> System.out.print("x".repeat(40_000));
                default -> throw new IllegalArgumentException();
            }
        }
    }
}
