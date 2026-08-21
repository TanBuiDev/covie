package vn.covie.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.covie.catalog.api.dto.discovery.CatalogDiscoveryQuery;
import vn.covie.catalog.api.dto.discovery.CatalogSort;
import vn.covie.catalog.persistence.CatalogDiscoveryRepository;
import vn.covie.location.application.LocationContext;
import vn.covie.location.application.LocationContextResolver;
import vn.covie.location.application.LocationResolutionSource;
import vn.covie.media.storage.StorageProperties;
import vn.covie.shipping.model.ShippingMode;
import vn.covie.users.model.UserAddressEntity;

class CatalogDiscoveryFulfillmentFilterTest {
    @Test
    void preservesCanServeAndShippingModeInNormalizedRepositoryQuery() {
        CatalogDiscoveryRepository repository = mock(CatalogDiscoveryRepository.class);
        when(repository.find(any(), any(), any(), any())).thenReturn(List.of());
        LocationContextResolver contexts = mock(LocationContextResolver.class);
        when(contexts.resolveForDiscovery(any(), any(), any())).thenReturn(new LocationContext(
                LocationResolutionSource.COORDINATES, UserAddressEntity.point(106.7, 10.8), null, null,
                ZoneId.of("Asia/Ho_Chi_Minh")));
        CatalogDiscoveryService service = new CatalogDiscoveryService(repository, contexts, () -> Instant.EPOCH,
                new StorageProperties("bucket", "region", URI.create("https://s3.test"), URI.create("https://cdn.test"),
                        true, Duration.ofMinutes(1), 1024, 1));

        service.discover(new CatalogDiscoveryQuery(null, null, null, null, 10.8, 106.7, null, 5_000,
                null, null, null, null, CatalogSort.DISTANCE, 20, null, true, ShippingMode.FREE));

        ArgumentCaptor<CatalogDiscoveryQuery> query = ArgumentCaptor.forClass(CatalogDiscoveryQuery.class);
        verify(repository).find(query.capture(), any(), any(), any());
        assertThat(query.getValue().canServe()).isTrue();
        assertThat(query.getValue().shipping()).isEqualTo(ShippingMode.FREE);
    }
}
