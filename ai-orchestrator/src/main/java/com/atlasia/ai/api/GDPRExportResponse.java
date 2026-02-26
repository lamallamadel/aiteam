package com.atlasia.ai.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class GDPRExportResponse {
    private Instant exportTimestamp;
    private UUID userId;
    private Map<String, Object> user;
    private List<Map<String, Object>> authenticationEvents;
    private List<Map<String, Object>> accessLogs;
    private List<Map<String, Object>> dataMutations;
    private List<Map<String, Object>> adminActionsAsTarget;
    private List<Map<String, Object>> adminActionsAsAdmin;
    private List<Map<String, Object>> collaborationEvents;
    private int totalRecords;

    public GDPRExportResponse() {}

    public Instant getExportTimestamp() { return exportTimestamp; }
    public void setExportTimestamp(Instant exportTimestamp) { this.exportTimestamp = exportTimestamp; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public Map<String, Object> getUser() { return user; }
    public void setUser(Map<String, Object> user) { this.user = user; }

    public List<Map<String, Object>> getAuthenticationEvents() { return authenticationEvents; }
    public void setAuthenticationEvents(List<Map<String, Object>> authenticationEvents) { 
        this.authenticationEvents = authenticationEvents; 
    }

    public List<Map<String, Object>> getAccessLogs() { return accessLogs; }
    public void setAccessLogs(List<Map<String, Object>> accessLogs) { this.accessLogs = accessLogs; }

    public List<Map<String, Object>> getDataMutations() { return dataMutations; }
    public void setDataMutations(List<Map<String, Object>> dataMutations) { this.dataMutations = dataMutations; }

    public List<Map<String, Object>> getAdminActionsAsTarget() { return adminActionsAsTarget; }
    public void setAdminActionsAsTarget(List<Map<String, Object>> adminActionsAsTarget) { 
        this.adminActionsAsTarget = adminActionsAsTarget; 
    }

    public List<Map<String, Object>> getAdminActionsAsAdmin() { return adminActionsAsAdmin; }
    public void setAdminActionsAsAdmin(List<Map<String, Object>> adminActionsAsAdmin) { 
        this.adminActionsAsAdmin = adminActionsAsAdmin; 
    }

    public List<Map<String, Object>> getCollaborationEvents() { return collaborationEvents; }
    public void setCollaborationEvents(List<Map<String, Object>> collaborationEvents) { 
        this.collaborationEvents = collaborationEvents; 
    }

    public int getTotalRecords() { return totalRecords; }
    public void setTotalRecords(int totalRecords) { this.totalRecords = totalRecords; }
}
