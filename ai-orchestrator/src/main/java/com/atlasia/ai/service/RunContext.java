package com.atlasia.ai.service;

import com.atlasia.ai.model.RunEntity;

import java.util.Map;

public class RunContext {
    private final RunEntity runEntity;
    private final String owner;
    private final String repo;
    private Map<String, Object> issueData;
    private String ticketPlan;
    private String workPlan;
    private String architectureNotes;
    private String prUrl;
    private String branchName;

    public RunContext(RunEntity runEntity, String owner, String repo) {
        this.runEntity = runEntity;
        this.owner = owner;
        this.repo = repo;
    }

    public RunEntity getRunEntity() {
        return runEntity;
    }

    public String getOwner() {
        return owner;
    }

    public String getRepo() {
        return repo;
    }

    public Map<String, Object> getIssueData() {
        return issueData;
    }

    public void setIssueData(Map<String, Object> issueData) {
        this.issueData = issueData;
    }

    public String getTicketPlan() {
        return ticketPlan;
    }

    public void setTicketPlan(String ticketPlan) {
        this.ticketPlan = ticketPlan;
    }

    public String getWorkPlan() {
        return workPlan;
    }

    public void setWorkPlan(String workPlan) {
        this.workPlan = workPlan;
    }

    public String getArchitectureNotes() {
        return architectureNotes;
    }

    public void setArchitectureNotes(String architectureNotes) {
        this.architectureNotes = architectureNotes;
    }

    public String getPrUrl() {
        return prUrl;
    }

    public void setPrUrl(String prUrl) {
        this.prUrl = prUrl;
    }

    public String getBranchName() {
        return branchName;
    }

    public void setBranchName(String branchName) {
        this.branchName = branchName;
    }
}
