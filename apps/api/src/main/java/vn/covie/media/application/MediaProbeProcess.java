package vn.covie.media.application;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

final class MediaProbeProcess {
    private static final int MAX_OUTPUT_BYTES = 32_768;

    private MediaProbeProcess() {}

    static String run(List<String> command, Duration timeout) throws IOException {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        long deadline = System.nanoTime() + timeout.toNanos();
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        try (var input = process.getInputStream(); var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var output = executor.submit(() -> input.readNBytes(MAX_OUTPUT_BYTES + 1));
            try {
                process.getOutputStream().close();
                if (!process.waitFor(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS))
                    throw new IOException("Media probe process timed out.");
                byte[] bytes = output.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
                if (bytes.length > MAX_OUTPUT_BYTES) throw new IOException("Media probe output exceeds limit.");
                if (process.exitValue() != 0) throw new Failed();
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Media probe process interrupted.", interrupted);
            } catch (TimeoutException timedOut) {
                throw new IOException("Media probe process timed out.", timedOut);
            } catch (ExecutionException readFailure) {
                throw new IOException("Media probe output could not be read.", readFailure);
            } finally {
                process.descendants().forEach(ProcessHandle::destroyForcibly);
                process.destroyForcibly();
                output.cancel(true);
            }
        }
    }

    static final class Failed extends IOException {
        Failed() { super("Media probe process failed."); }
    }
}
