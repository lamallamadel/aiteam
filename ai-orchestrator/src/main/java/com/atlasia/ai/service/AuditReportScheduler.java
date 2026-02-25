package com.atlasia.ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Component
public class AuditReportScheduler {

    private static final Logger logger = LoggerFactory.getLogger(AuditReportScheduler.class);
    
    private final ComplianceReportService complianceReportService;

    public AuditReportScheduler(ComplianceReportService complianceReportService) {
        this.complianceReportService = complianceReportService;
    }

    @Scheduled(cron = "0 0 3 1 * ?")
    public void generateMonthlySOC2Report() {
        logger.info("Starting scheduled monthly SOC2 compliance report generation");
        try {
            Instant now = Instant.now();
            Instant periodEnd = now.truncatedTo(ChronoUnit.DAYS);
            Instant periodStart = periodEnd.minus(30, ChronoUnit.DAYS);
            
            String filePath = complianceReportService.generateSOC2Report(
                periodStart, periodEnd, "SYSTEM_SCHEDULER");
            
            logger.info("Successfully generated monthly SOC2 report: {}", filePath);
        } catch (Exception e) {
            logger.error("Failed to generate scheduled SOC2 report", e);
        }
    }

    @Scheduled(cron = "0 30 3 1 * ?")
    public void generateMonthlyISO27001Report() {
        logger.info("Starting scheduled monthly ISO 27001 compliance report generation");
        try {
            Instant now = Instant.now();
            Instant periodEnd = now.truncatedTo(ChronoUnit.DAYS);
            Instant periodStart = periodEnd.minus(30, ChronoUnit.DAYS);
            
            String filePath = complianceReportService.generateISO27001Report(
                periodStart, periodEnd, "SYSTEM_SCHEDULER");
            
            logger.info("Successfully generated monthly ISO 27001 report: {}", filePath);
        } catch (Exception e) {
            logger.error("Failed to generate scheduled ISO 27001 report", e);
        }
    }

    @Scheduled(cron = "0 0 4 1 */3 ?")
    public void generateQuarterlySOC2Report() {
        logger.info("Starting scheduled quarterly SOC2 compliance report generation");
        try {
            Instant now = Instant.now();
            Instant periodEnd = now.truncatedTo(ChronoUnit.DAYS);
            Instant periodStart = periodEnd.minus(90, ChronoUnit.DAYS);
            
            String filePath = complianceReportService.generateSOC2Report(
                periodStart, periodEnd, "SYSTEM_SCHEDULER_QUARTERLY");
            
            logger.info("Successfully generated quarterly SOC2 report: {}", filePath);
        } catch (Exception e) {
            logger.error("Failed to generate scheduled quarterly SOC2 report", e);
        }
    }

    @Scheduled(cron = "0 30 4 1 */3 ?")
    public void generateQuarterlyISO27001Report() {
        logger.info("Starting scheduled quarterly ISO 27001 compliance report generation");
        try {
            Instant now = Instant.now();
            Instant periodEnd = now.truncatedTo(ChronoUnit.DAYS);
            Instant periodStart = periodEnd.minus(90, ChronoUnit.DAYS);
            
            String filePath = complianceReportService.generateISO27001Report(
                periodStart, periodEnd, "SYSTEM_SCHEDULER_QUARTERLY");
            
            logger.info("Successfully generated quarterly ISO 27001 report: {}", filePath);
        } catch (Exception e) {
            logger.error("Failed to generate scheduled quarterly ISO 27001 report", e);
        }
    }
}
