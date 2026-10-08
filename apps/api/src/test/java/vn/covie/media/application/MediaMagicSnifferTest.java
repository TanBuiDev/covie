package vn.covie.media.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MediaMagicSnifferTest {
    private final MediaMagicSniffer sniffer = new MediaMagicSniffer();

    @Test
    void verifiesVideoContainerAndRejectsUnknownBrandsAndMatroska() {
        assertThat(sniffer.matches("video/mp4", new byte[] {0,0,0,20,102,116,121,112,105,115,111,109,0,0,0,0,109,112,52,50})).isTrue();
        assertThat(sniffer.matches("video/mp4", new byte[] {0,0,0,16,102,116,121,112,113,116,32,32,0,0,0,0})).isFalse();
        assertThat(sniffer.matches("video/webm", new byte[] {26,69,(byte)223,(byte)163,(byte)135,66,(byte)130,(byte)132,119,101,98,109})).isTrue();
        assertThat(sniffer.matches("video/webm", new byte[] {26,69,(byte)223,(byte)163,(byte)139,66,(byte)130,(byte)136,109,97,116,114,111,115,107,97})).isFalse();
        assertThat(sniffer.matches("video/webm", new byte[] {26,69,(byte)223,(byte)163,119,101,98,109})).isFalse();
    }

    @Test
    void acceptsOnlyTheDeclaredImageSignatures() {
        assertThat(sniffer.matches("image/jpeg", new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})).isTrue();
        assertThat(sniffer.matches("image/png", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a})).isTrue();
        assertThat(sniffer.matches("image/webp", new byte[] {0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x45, 0x42, 0x50})).isTrue();
        assertThat(sniffer.matches("image/png", new byte[] {1, 2, 3, 4})).isFalse();
        assertThat(sniffer.matches("image/svg+xml", new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47})).isFalse();
    }
}
