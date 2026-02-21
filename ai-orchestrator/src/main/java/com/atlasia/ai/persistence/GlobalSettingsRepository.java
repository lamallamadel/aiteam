package com.atlasia.ai.persistence;

import com.atlasia.ai.model.GlobalSettingsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GlobalSettingsRepository extends JpaRepository<GlobalSettingsEntity, String> {}
