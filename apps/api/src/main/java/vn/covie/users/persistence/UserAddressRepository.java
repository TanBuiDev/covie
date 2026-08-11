package vn.covie.users.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.users.model.UserAddressEntity;

public interface UserAddressRepository extends JpaRepository<UserAddressEntity, UUID> {
    List<UserAddressEntity> findAllByUserIdAndStatusOrderByUpdatedAtDesc(UUID userId, String status);
    Optional<UserAddressEntity> findByIdAndUserIdAndStatus(UUID id, UUID userId, String status);
    long countByUserIdAndStatus(UUID userId, String status);
}
