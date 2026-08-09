package vn.covie.users.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.users.model.ContactType;
import vn.covie.users.model.UserContactMethodEntity;

public interface UserContactMethodRepository extends JpaRepository<UserContactMethodEntity, UUID> {
    List<UserContactMethodEntity> findAllByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, String status);
    List<UserContactMethodEntity> findAllByUserIdAndContactTypeAndStatus(UUID userId, ContactType contactType, String status);
    Optional<UserContactMethodEntity> findByIdAndUserId(UUID id, UUID userId);
}
