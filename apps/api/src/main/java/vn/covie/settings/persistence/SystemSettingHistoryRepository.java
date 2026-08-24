package vn.covie.settings.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page; import org.springframework.data.domain.Pageable;
import vn.covie.settings.model.SystemSettingHistoryEntity;

public interface SystemSettingHistoryRepository extends JpaRepository<SystemSettingHistoryEntity, UUID> {
    List<SystemSettingHistoryEntity> findAllBySettingIdOrderByChangedAtDescIdDesc(UUID settingId);
    Page<SystemSettingHistoryEntity> findAllBySettingIdOrderByChangedAtDescIdDesc(UUID settingId, Pageable pageable);
}
