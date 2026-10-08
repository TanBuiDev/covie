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
            case "video/mp4" -> mp4(prefix);
            case "video/webm" -> webm(prefix);
            default -> false;
        };
    }
    private static boolean mp4(byte[] bytes) {
        if (bytes.length < 16 || !ascii(bytes, 4, "ftyp")) return false;
        long size = Integer.toUnsignedLong(java.nio.ByteBuffer.wrap(bytes, 0, 4).getInt());
        if (size < 16 || size > bytes.length || size % 4 != 0) return false;
        for (int offset = 8; offset < size; offset += 4) {
            if (offset == 12) continue;
            for (String brand : new String[] {"isom", "iso2", "iso3", "iso4", "iso5", "iso6", "mp41", "mp42", "avc1", "M4V "})
                if (ascii(bytes, offset, brand)) return true;
        }
        return false;
    }
    private static boolean webm(byte[] bytes) {
        if (!startsWith(bytes, 0x1a, 0x45, 0xdf, 0xa3)) return false;
        int[] header = vint(bytes, 4);
        if (header == null || header[1] > bytes.length - 4 - header[0]) return false;
        int offset = 4 + header[0], end = offset + header[1];
        while (offset < end) {
            int idLength = length(bytes[offset]);
            if (idLength < 1 || idLength > 4 || offset + idLength >= end) return false;
            boolean docType = idLength == 2 && (bytes[offset] & 255) == 0x42 && (bytes[offset + 1] & 255) == 0x82;
            offset += idLength;
            int[] size = vint(bytes, offset);
            if (size == null) return false;
            offset += size[0];
            if (size[1] > end - offset) return false;
            if (docType) return size[1] == 4 && ascii(bytes, offset, "webm");
            offset += size[1];
        }
        return false;
    }
    private static int length(byte first) {
        int value = first & 255;
        for (int length = 1; length <= 8; length++) if ((value & (1 << (8 - length))) != 0) return length;
        return 0;
    }
    private static int[] vint(byte[] bytes, int offset) {
        if (offset >= bytes.length) return null;
        int length = length(bytes[offset]);
        if (length < 1 || length > 4 || offset + length > bytes.length) return null;
        long value = bytes[offset] & ((1 << (8 - length)) - 1);
        for (int i = 1; i < length; i++) value = (value << 8) | (bytes[offset + i] & 255);
        if (value > 4096) return null;
        return new int[] {length, (int)value};
    }
    private static boolean ascii(byte[] bytes, int offset, String text) {
        if (offset + text.length() > bytes.length) return false;
        for (int i = 0; i < text.length(); i++) if (bytes[offset + i] != text.charAt(i)) return false;
        return true;
    }
    private static boolean startsWith(byte[] bytes, int... expected) {
        if (bytes.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) if ((bytes[i] & 0xff) != expected[i]) return false;
        return true;
    }
}
