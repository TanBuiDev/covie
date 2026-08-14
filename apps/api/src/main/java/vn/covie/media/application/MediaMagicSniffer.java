package vn.covie.media.application;

import java.util.Arrays;
import org.springframework.stereotype.Component;

@Component
public class MediaMagicSniffer {
    public boolean matches(String contentType, byte[] prefix) {
        if (prefix == null) return false;
        return switch (contentType) {
            case "image/jpeg" -> startsWith(prefix, 0xff, 0xd8, 0xff);
            case "image/png" -> startsWith(prefix, 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a);
            case "image/webp" -> startsWith(prefix, 0x52, 0x49, 0x46, 0x46) && prefix.length >= 12
                    && Arrays.equals(Arrays.copyOfRange(prefix, 8, 12), new byte[] {0x57, 0x45, 0x42, 0x50});
            default -> false;
        };
    }
    private static boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((bytes[i] & 0xff) != expected[i]) return false;
        return true;
    }
}
