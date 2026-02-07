package com.atlasia.ai.model;

import java.util.List;

public class TicketPlan {
    private int issueId;
    private String title;
    private String summary;
    private List<String> acceptanceCriteria;
    private List<String> outOfScope;
    private List<String> risks;
    private List<String> labelsToApply;

    public TicketPlan() {
    }

    public TicketPlan(int issueId, String title, String summary, List<String> acceptanceCriteria,
                      List<String> outOfScope, List<String> risks, List<String> labelsToApply) {
        this.issueId = issueId;
        this.title = title;
        this.summary = summary;
        this.acceptanceCriteria = acceptanceCriteria;
        this.outOfScope = outOfScope;
        this.risks = risks;
        this.labelsToApply = labelsToApply;
    }

    public int getIssueId() {
        return issueId;
    }

    public void setIssueId(int issueId) {
        this.issueId = issueId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getAcceptanceCriteria() {
        return acceptanceCriteria;
    }

    public void setAcceptanceCriteria(List<String> acceptanceCriteria) {
        this.acceptanceCriteria = acceptanceCriteria;
    }

    public List<String> getOutOfScope() {
        return outOfScope;
    }

    public void setOutOfScope(List<String> outOfScope) {
        this.outOfScope = outOfScope;
    }

    public List<String> getRisks() {
        return risks;
    }

    public void setRisks(List<String> risks) {
        this.risks = risks;
    }

    public List<String> getLabelsToApply() {
        return labelsToApply;
    }

    public void setLabelsToApply(List<String> labelsToApply) {
        this.labelsToApply = labelsToApply;
    }
}
