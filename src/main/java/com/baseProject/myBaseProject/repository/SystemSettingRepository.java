package com.baseProject.myBaseProject.repository;

import com.baseProject.myBaseProject.entity.SystemSetting;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SystemSettingRepository extends JpaRepository<SystemSetting, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT setting FROM SystemSetting setting WHERE setting.key = :key")
    Optional<SystemSetting> findByKeyForUpdate(@Param("key") String key);
}
