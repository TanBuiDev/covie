package vn.covie.settings.persistence;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import vn.covie.settings.model.SystemSettingHistoryEntity;

public interface SystemSettingHistoryRepository extends JpaRepository<SystemSettingHistoryEntity, UUID> {
    Page<SystemSettingHistoryEntity> findAllBySettingIdOrderByChangedAtDescIdDesc(UUID settingId, Pageable pageable);
}
