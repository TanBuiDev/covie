package vn.covie.reputation.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.reputation.model.ReputationOrderOutcomeEntity;

public interface ReputationOrderOutcomeRepository extends JpaRepository<ReputationOrderOutcomeEntity, UUID> {}
