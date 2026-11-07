package com.quadmlabs.saphikuna.repository;

import com.quadmlabs.saphikuna.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SettingRepository extends JpaRepository<Setting, String> {
}
