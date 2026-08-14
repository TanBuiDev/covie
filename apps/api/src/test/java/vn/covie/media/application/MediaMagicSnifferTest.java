package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MediaMagicSnifferTest {
    private final MediaMagicSniffer sniffer = new MediaMagicSniffer();

    @Test
    void acceptsOnlyTheDeclaredImageSignatures() {
        assertThat(sniffer.matches("image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})).isTrue();
        assertThat(sniffer.matches("image/png", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})).isTrue();
        assertThat(sniffer.matches("image/webp", new byte[] {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50})).isTrue();
        assertThat(sniffer.matches("image/png", new byte[] {1, 2, 3, 4})).isFalse();
        assertThat(sniffer.matches("image/svg+xml", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47})).isFalse();
    }
}
