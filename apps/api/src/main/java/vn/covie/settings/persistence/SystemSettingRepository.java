package vn.covie.settings.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
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
    List<SystemSettingEntity> findAllBySettingKeyOrderByScopeTypeAsc(String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemSettingEntity s where s.settingKey=:key and s.scopeType=:scope")
    Optional<SystemSettingEntity> lockGlobal(@Param("key") String key, @Param("scope") SettingScopeType scope);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemSettingEntity s where s.settingKey=:key and s.scopeType=:scope and s.merchantProfileId=:scopeId")
    Optional<SystemSettingEntity> lockMerchant(@Param("key") String key, @Param("scope") SettingScopeType scope, @Param("scopeId") UUID scopeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemSettingEntity s where s.settingKey=:key and s.scopeType=:scope and s.categoryId=:scopeId")
    Optional<SystemSettingEntity> lockCategory(@Param("key") String key, @Param("scope") SettingScopeType scope, @Param("scopeId") UUID scopeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SystemSettingEntity s where s.settingKey=:key and s.scopeType=:scope and s.locationAreaId=:scopeId")
    Optional<SystemSettingEntity> lockLocationArea(@Param("key") String key, @Param("scope") SettingScopeType scope, @Param("scopeId") UUID scopeId);
}
