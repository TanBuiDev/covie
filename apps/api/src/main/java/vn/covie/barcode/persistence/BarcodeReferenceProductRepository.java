package vn.covie.barcode.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.covie.barcode.model.BarcodeReferenceProductEntity;

@Repository
public interface BarcodeReferenceProductRepository extends JpaRepository<BarcodeReferenceProductEntity, Long> {

    Optional<BarcodeReferenceProductEntity> findFirstByGtin14AndActiveTrue(String gtin14);
}
