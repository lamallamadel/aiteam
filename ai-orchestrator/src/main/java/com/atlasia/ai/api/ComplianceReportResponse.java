package com.atlasia.ai.api;

public class ComplianceReportResponse {
    private String status;
    private String filePath;
    private String message;

    public ComplianceReportResponse() {}

    public ComplianceReportResponse(String status, String filePath, String message) {
        this.status = status;
        this.filePath = filePath;
        this.message = message;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
