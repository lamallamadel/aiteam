package com.atlasia.ai.persistence;

import com.atlasia.ai.model.NotificationDeliveryLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface NotificationDeliveryLogRepository extends JpaRepository<NotificationDeliveryLogEntity, UUID> {

    List<NotificationDeliveryLogEntity> findByNotificationConfigId(UUID notificationConfigId);

    List<NotificationDeliveryLogEntity> findByStatusAndRetryCountLessThan(String status, int maxRetries);

    @Query("SELECT n FROM NotificationDeliveryLogEntity n WHERE n.createdAt BETWEEN :start AND :end ORDER BY n.createdAt DESC")
    List<NotificationDeliveryLogEntity> findByCreatedAtBetween(
            @Param("start") Instant start,
            @Param("end") Instant end);
}
