package vn.covie.media.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;

public final class CatalogVideoProbe {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final Path ffprobe;
    private final Path ffmpeg;
    private final Duration timeout;

    public CatalogVideoProbe(Path ffprobe, Path ffmpeg, Duration timeout) {
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
        this.ffprobe = ffprobe;
        this.ffmpeg = ffmpeg;
        this.timeout = timeout;
    }

    public Metadata inspect(Path file, String contentType) throws IOException {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) == 0)
            throw new InvalidVideoException();
        if (!Set.of("video/mp4", "video/webm").contains(contentType)) throw invalidMetadata();
        String input = file.toAbsolutePath().toString();
        long deadline = System.nanoTime() + timeout.toNanos();
        try {
            String output = MediaProbeProcess.run(List.of(ffprobe.toString(), "-v", "error", "-max_alloc", "67108864",
                    "-protocol_whitelist", "file", "-format_whitelist", "mov,matroska,webm", "-threads", "1",
                    "-show_entries", "stream=codec_type,codec_name,width,height:format=format_name,duration", "-of", "json", input), remaining(deadline));
            Metadata metadata = parseMetadata(output, contentType);
            MediaProbeProcess.run(List.of(ffmpeg.toString(), "-v", "error", "-nostdin", "-xerror", "-max_error_rate", "0",
                    "-max_alloc", "67108864", "-protocol_whitelist", "file", "-format_whitelist", "mov,matroska,webm",
                    "-threads", "1", "-err_detect", "explode", "-i", input, "-map", "0:v:0", "-map", "0:a?",
                    "-sn", "-dn", "-abort_on", "empty_output", "-f", "null", "-"), remaining(deadline));
            return metadata;
        } catch (MediaProbeProcess.Failed invalidFile) { throw new InvalidVideoException(); }
    }

    static Metadata parseMetadata(String output, String contentType) throws IOException {
        try {
            boolean mp4 = "video/mp4".equals(contentType);
            if (!mp4 && !"video/webm".equals(contentType)) throw invalidMetadata();
            JsonNode root = JSON.readTree(output);
            Set<String> formats = Set.of(root.path("format").path("format_name").asText().split(","));
            if (!formats.contains(mp4 ? "mp4" : "webm")) throw invalidMetadata();
            BigDecimal seconds = new BigDecimal(root.path("format").path("duration").asText());
            if (seconds.signum() <= 0 || seconds.compareTo(BigDecimal.valueOf(60)) > 0) throw invalidMetadata();
            JsonNode streams = root.path("streams");
            if (!streams.isArray()) throw invalidMetadata();
            JsonNode video = null;
            for (JsonNode stream : streams) {
                String type = stream.path("codec_type").asText();
                String codec = stream.path("codec_name").asText();
                if ("video".equals(type)) {
                    if (video != null || !(mp4 ? Set.of("h264") : Set.of("vp8", "vp9")).contains(codec)) throw invalidMetadata();
                    video = stream;
                } else if (!"audio".equals(type) || !(mp4 ? Set.of("aac") : Set.of("opus", "vorbis")).contains(codec)) {
                    throw invalidMetadata();
                }
            }
            if (video == null || !video.path("width").canConvertToInt() || !video.path("height").canConvertToInt()
                    || video.path("width").asInt() <= 0 || video.path("height").asInt() <= 0) throw invalidMetadata();
            return new Metadata(video.path("width").asInt(), video.path("height").asInt(),
                    seconds.movePointRight(3).setScale(0, RoundingMode.CEILING).longValueExact(), video.path("codec_name").asText());
        } catch (RuntimeException invalid) {
            throw invalidMetadata();
        } catch (IOException invalid) {
            throw invalidMetadata();
        }
    }

    private static Duration remaining(long deadline) throws IOException {
        long nanos = deadline - System.nanoTime();
        if (nanos <= 0) throw new IOException("Catalog video verification timed out.");
        return Duration.ofNanos(nanos);
    }

    private static InvalidVideoException invalidMetadata() { return new InvalidVideoException(); }

    public static final class InvalidVideoException extends IOException {
        public InvalidVideoException() { super("Unsupported or invalid catalog video metadata."); }
    }

    public record Metadata(int width, int height, long durationMillis, String codec) {}
}
