package vn.covie.users.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.users.model.UserContactMethodEntity;

public interface UserContactMethodRepository extends JpaRepository<UserContactMethodEntity, UUID> {}
