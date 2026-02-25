package com.atlasia.ai.persistence;

import com.atlasia.ai.model.ComplianceReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ComplianceReportRepository extends JpaRepository<ComplianceReportEntity, UUID> {
    List<ComplianceReportEntity> findByReportTypeOrderByGeneratedAtDesc(String reportType);
    
    List<ComplianceReportEntity> findByGeneratedAtBetweenOrderByGeneratedAtDesc(Instant start, Instant end);
    
    List<ComplianceReportEntity> findByReportTypeAndReportPeriodStartAndReportPeriodEnd(
        String reportType, Instant periodStart, Instant periodEnd);
}
