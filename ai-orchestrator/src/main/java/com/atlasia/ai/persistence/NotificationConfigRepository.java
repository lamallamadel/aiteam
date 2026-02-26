package com.atlasia.ai.persistence;

import com.atlasia.ai.model.NotificationConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationConfigRepository extends JpaRepository<NotificationConfigEntity, UUID> {

    List<NotificationConfigEntity> findByUserId(UUID userId);

    List<NotificationConfigEntity> findByUserIdAndEnabled(UUID userId, boolean enabled);
}
