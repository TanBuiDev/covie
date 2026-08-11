package vn.covie.merchants.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.merchants.model.MerchantContactMethodEntity;

public interface MerchantContactMethodRepository extends JpaRepository<MerchantContactMethodEntity, UUID> {}
