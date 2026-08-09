package vn.covie.users.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import vn.covie.support.PostgresIntegrationTest;
import vn.covie.users.model.PinSource;
import vn.covie.users.model.UserAddressEntity;
import vn.covie.users.model.UserEntity;

class UserAddressPostgisIntegrationTest extends PostgresIntegrationTest {
    @Autowired UserRepository users;
    @Autowired UserAddressRepository addresses;
    @Autowired EntityManager entityManager;

    @Test
    @Transactional
    void persistsLongitudeAsXLatitudeAsYAndUsesWgs84() {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        users.save(UserEntity.active(userId, "Address owner", Instant.parse("2026-08-09T00:00:00Z")));
        addresses.save(UserAddressEntity.create(addressId, userId, "Home", null, null, "1 Main Street", 10.8412, 106.8374,
                PinSource.GPS, null, true, Instant.parse("2026-08-09T00:00:00Z")));
        entityManager.flush();
        entityManager.clear();

        UserAddressEntity reloaded = addresses.findById(addressId).orElseThrow();
        Object[] sqlPoint = (Object[]) entityManager.createNativeQuery("""
                select ST_X(geo_point::geometry), ST_Y(geo_point::geometry)
                from user_addresses where id = :id
                """).setParameter("id", addressId).getSingleResult();

        assertThat(reloaded.getGeoPoint().getX()).isEqualTo(106.8374);
        assertThat(reloaded.getGeoPoint().getY()).isEqualTo(10.8412);
        assertThat(reloaded.getGeoPoint().getSRID()).isEqualTo(4326);
        assertThat(((Number) sqlPoint[0]).doubleValue()).isEqualTo(106.8374);
        assertThat(((Number) sqlPoint[1]).doubleValue()).isEqualTo(10.8412);
    }
}
