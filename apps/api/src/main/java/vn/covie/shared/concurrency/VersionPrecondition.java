package vn.covie.shared.concurrency;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import vn.covie.shared.web.ApiException;
import vn.covie.shared.web.ErrorCode;

public final class VersionPrecondition {
    private static final Pattern ETAG = Pattern.compile("\\\"v(\\d+)\\\"");

    private VersionPrecondition() {}

    public static String format(long version) {
        return "\"v" + version + "\"";
    }

    public static long parseRequired(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new ApiException(
                    ErrorCode.IF_MATCH_REQUIRED,
                    HttpStatus.PRECONDITION_REQUIRED,
                    "If-Match is required.");
        }
        Matcher matcher = ETAG.matcher(raw.strip());
        if (!matcher.matches()) {
            throw new ApiException(
                    ErrorCode.INVALID_IF_MATCH,
                    HttpStatus.BAD_REQUEST,
                    "Invalid If-Match value.");
        }
        return Long.parseLong(matcher.group(1));
    }

    public static void require(long expected, long current) {
        if (expected != current) {
            throw new ApiException(
                    ErrorCode.RESOURCE_VERSION_CONFLICT,
                    HttpStatus.CONFLICT,
                    "Resource version has changed.",
                    Map.of("expectedVersion", expected, "currentVersion", current));
        }
    }
}
