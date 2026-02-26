package com.atlasia.ai.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "compliance_reports", indexes = {
    @Index(name = "idx_compliance_reports_type", columnList = "report_type"),
    @Index(name = "idx_compliance_reports_generated_at", columnList = "generated_at")
})
public class ComplianceReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "report_type", nullable = false, length = 50)
    private String reportType;

    @Column(name = "report_period_start", nullable = false)
    private Instant reportPeriodStart;

    @Column(name = "report_period_end", nullable = false)
    private Instant reportPeriodEnd;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "generated_by")
    private String generatedBy;

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "record_count")
    private Integer recordCount;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    protected ComplianceReportEntity() {}

    public ComplianceReportEntity(String reportType, Instant reportPeriodStart, Instant reportPeriodEnd,
                                 Instant generatedAt, String generatedBy, String filePath,
                                 Integer recordCount, String status) {
        this.reportType = reportType;
        this.reportPeriodStart = reportPeriodStart;
        this.reportPeriodEnd = reportPeriodEnd;
        this.generatedAt = generatedAt;
        this.generatedBy = generatedBy;
        this.filePath = filePath;
        this.recordCount = recordCount;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getReportType() { return reportType; }
    public Instant getReportPeriodStart() { return reportPeriodStart; }
    public Instant getReportPeriodEnd() { return reportPeriodEnd; }
    public Instant getGeneratedAt() { return generatedAt; }
    public String getGeneratedBy() { return generatedBy; }
    public String getFilePath() { return filePath; }
    public Integer getRecordCount() { return recordCount; }
    public String getStatus() { return status; }

    public void setFilePath(String filePath) { this.filePath = filePath; }
    public void setRecordCount(Integer recordCount) { this.recordCount = recordCount; }
    public void setStatus(String status) { this.status = status; }
}
