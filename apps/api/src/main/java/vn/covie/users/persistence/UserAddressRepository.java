package vn.covie.users.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.users.model.UserAddressEntity;

public interface UserAddressRepository extends JpaRepository<UserAddressEntity, UUID> {}
