package vn.covie.media.storage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProductionStorageContractTest {
    @Test
    void acceptsPrivateProductionShapedConfiguration() {
        StorageProperties p = new StorageProperties("covie-media", "ap-southeast-1", null,
                URI.create("https://media.covie.vn"), false, Duration.ofMinutes(10), 10L * 1024 * 1024, 20);
        assertDoesNotThrow(() -> ProductionStorageValidator.validate(p));
    }

    @Test
    void rejectsLocalEndpointOrNonHttpsPublicUrl() {
        StorageProperties local = new StorageProperties("covie-media", "ap-southeast-1", URI.create("http://localhost:9000"),
                URI.create("http://localhost:9000/covie-media"), true, Duration.ofMinutes(10), 10L * 1024 * 1024, 20);
        assertThrows(IllegalStateException.class, () -> ProductionStorageValidator.validate(local));
    }

    @Test
    void rejectsPresignTtlAboveTenMinutes() {
        StorageProperties p = new StorageProperties("covie-media", "ap-southeast-1", null,
                URI.create("https://media.covie.vn"), false, Duration.ofMinutes(11), 10L * 1024 * 1024, 20);
        assertThrows(IllegalStateException.class, () -> ProductionStorageValidator.validate(p));
    }

    @Test
    void rejectsHttpsPublicUrlWithoutAHost() {
        StorageProperties p = new StorageProperties("covie-media", "ap-southeast-1", null,
                URI.create("https:/media"), false, Duration.ofMinutes(10), 1024, 20);
        assertThrows(IllegalStateException.class, () -> ProductionStorageValidator.validate(p));
    }

    @Test
    void rejectsHttpsIpv6LoopbackEndpoint() {
        StorageProperties p = new StorageProperties("covie-media", "ap-southeast-1", URI.create("https://[::1]:9000"),
                URI.create("https://media.covie.vn"), false, Duration.ofMinutes(10), 1024, 20);
        assertThrows(IllegalStateException.class, () -> ProductionStorageValidator.validate(p));
    }

    @Test
    void rejectsExpandedLoopbackAndPreservesNonLoopbackIpv6Endpoint() {
        StorageProperties loopback = new StorageProperties("covie-media", "ap-southeast-1",
                URI.create("https://[0:0:0:0:0:0:0:1]:9000"), URI.create("https://media.covie.vn"),
                false, Duration.ofMinutes(10), 1024, 20);
        assertThrows(IllegalStateException.class, () -> ProductionStorageValidator.validate(loopback));
        StorageProperties remote = new StorageProperties("covie-media", "ap-southeast-1",
                URI.create("https://[2001:db8::10]:9000"), URI.create("https://media.covie.vn"),
                false, Duration.ofMinutes(10), 1024, 20);
        assertDoesNotThrow(() -> ProductionStorageValidator.validate(remote));
    }
}
