package vn.covie.settings.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import vn.covie.settings.model.SettingScopeType;
import vn.covie.settings.model.SystemSettingEntity;

public interface SystemSettingRepository extends JpaRepository<SystemSettingEntity, UUID> {
    Optional<SystemSettingEntity> findBySettingKeyAndScopeType(String key, SettingScopeType scope);
    Optional<SystemSettingEntity> findBySettingKeyAndScopeTypeAndMerchantProfileId(String key, SettingScopeType scope, UUID merchantId);
    Optional<SystemSettingEntity> findBySettingKeyAndScopeTypeAndCategoryId(String key, SettingScopeType scope, UUID categoryId);
    Optional<SystemSettingEntity> findBySettingKeyAndScopeTypeAndLocationAreaId(String key, SettingScopeType scope, UUID areaId);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SystemSettingEntity s where s.id = :id") Optional<SystemSettingEntity> findByIdForUpdate(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from SystemSettingEntity s where s.settingKey = :key and s.scopeType = :scope") Optional<SystemSettingEntity> findBySettingKeyAndScopeTypeForUpdate(@Param("key") String key, @Param("scope") SettingScopeType scope);
}
