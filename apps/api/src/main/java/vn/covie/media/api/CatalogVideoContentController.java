package vn.covie.media.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.springframework.http.HttpRange;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.auth.security.CurrentActorProvider;
import vn.covie.media.application.CatalogVideoContentService;

@RestController
public class CatalogVideoContentController {
    private final CatalogVideoContentService content;
    private final CurrentActorProvider actors;
    private final io.micrometer.core.instrument.Counter deliveryFailures;

    public CatalogVideoContentController(CatalogVideoContentService content, CurrentActorProvider actors,
            io.micrometer.core.instrument.MeterRegistry metrics) {
        this.content = content;
        this.actors = actors;
        this.deliveryFailures = metrics.counter("covie.catalog.video.delivery.failures");
    }

    @io.swagger.v3.oas.annotations.Operation(summary = "Read verified catalog video with current public or private access")
    @io.swagger.v3.oas.annotations.responses.ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Full video or HEAD metadata", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "video/*", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "206", description = "Single byte range", content = @io.swagger.v3.oas.annotations.media.Content(mediaType = "video/*", schema = @io.swagger.v3.oas.annotations.media.Schema(type = "string", format = "binary"))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Unavailable or unreadable video", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "416", description = "Unsupported or unsatisfiable byte range", content = @io.swagger.v3.oas.annotations.media.Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "503", description = "Storage unavailable", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    @RequestMapping(path = "/api/v1/media/{mediaId}/content", method = {RequestMethod.GET, RequestMethod.HEAD})
    public void read(@PathVariable UUID mediaId, @RequestHeader(value = "Range", required = false) String range,
            HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        response.setHeader("Cache-Control", "no-store");
        var actor = authentication != null && authentication.getPrincipal() instanceof Jwt ? actors.currentActor() : null;
        var asset = content.requireReadable(mediaId, actor);
        boolean head = "HEAD".equals(request.getMethod());
        long first = 0;
        long last = asset.getSizeBytes() - 1;
        boolean partial = range != null && !head && request.getHeader("If-Range") == null;
        if (partial) {
            try {
                if (range.length() > 128) throw new IllegalArgumentException();
                var ranges = HttpRange.parseRanges(range);
                if (ranges.size() != 1) throw new IllegalArgumentException();
                first = ranges.getFirst().getRangeStart(asset.getSizeBytes());
                last = ranges.getFirst().getRangeEnd(asset.getSizeBytes());
                if (first < 0 || first > last || last >= asset.getSizeBytes()) throw new IllegalArgumentException();
            } catch (IllegalArgumentException invalid) {
                response.setStatus(416);
                response.setHeader("Content-Range", "bytes */" + asset.getSizeBytes());
                return;
            }
        }
        long length = last - first + 1;
        if (head) {
            response.setContentType(asset.getMimeType());
            response.setContentLengthLong(length);
            response.setHeader("Accept-Ranges", "bytes");
            return;
        }
        try (var input = content.openRange(asset, first, last)) {
            response.setStatus(partial ? 206 : 200);
            response.setContentType(asset.getMimeType());
            response.setContentLengthLong(length);
            response.setHeader("Accept-Ranges", "bytes");
            if (partial) response.setHeader("Content-Range", "bytes " + first + "-" + last + "/" + asset.getSizeBytes());
            byte[] buffer = new byte[8192];
            long remaining = length;
            while (remaining > 0) {
                int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (count < 0) throw new IOException("Truncated video content.");
                response.getOutputStream().write(buffer, 0, count);
                remaining -= count;
            }
            if (input.read() != -1) throw new IOException("Unexpected video content length.");
        } catch (IOException failure) {
            deliveryFailures.increment();
            if (response.isCommitted()) throw failure;
            response.reset();
            response.setHeader("Cache-Control", "no-store");
            throw CatalogVideoContentService.unavailable();
        } catch (vn.covie.shared.web.ApiException failure) {
            deliveryFailures.increment();
            throw failure;
        }
    }
}
