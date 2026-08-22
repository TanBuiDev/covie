package vn.covie.reputation.persistence;
import java.util.UUID; import org.springframework.data.jpa.repository.JpaRepository; import vn.covie.reputation.model.MerchantReputationStatsEntity;
public interface MerchantReputationStatsRepository extends JpaRepository<MerchantReputationStatsEntity, UUID> {}
