package vn.covie.reputation.persistence;
import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository; import vn.covie.reputation.model.UserReputationStatsEntity;
public interface UserReputationStatsRepository extends JpaRepository<UserReputationStatsEntity, UUID> {}
