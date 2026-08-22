package vn.covie.reputation.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.reputation.model.ReputationOrderOutcomeEntity;

public interface ReputationOrderOutcomeRepository extends JpaRepository<ReputationOrderOutcomeEntity, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select o from ReputationOrderOutcomeEntity o where o.orderId=:orderId")
    Optional<ReputationOrderOutcomeEntity> findForUpdate(@Param("orderId") UUID orderId);
}
