package vn.covie.merchants.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.merchants.model.MerchantLocationHourExceptionEntity;

public interface MerchantLocationHourExceptionRepository extends JpaRepository<MerchantLocationHourExceptionEntity, UUID> {}
