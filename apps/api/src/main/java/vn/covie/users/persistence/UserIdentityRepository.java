package vn.covie.users.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.users.model.IdentityProvider;
import vn.covie.users.model.UserIdentityEntity;

public interface UserIdentityRepository extends JpaRepository<UserIdentityEntity, UUID> {
    Optional<UserIdentityEntity> findByProviderAndProviderSubject(IdentityProvider provider, String subject);
    @Query("select i from UserIdentityEntity i where i.provider = vn.covie.users.model.IdentityProvider.EMAIL and i.emailNormalized = :email")
    Optional<UserIdentityEntity> findLocalEmail(@Param("email") String email);
    List<UserIdentityEntity> findAllByUserId(UUID userId);
}
