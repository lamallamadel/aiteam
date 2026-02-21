package com.atlasia.ai.model;

import com.atlasia.ai.service.DeveloperStep;
import com.atlasia.ai.service.RunContext;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * TEA Environment Snapshot — serializable capture of all RunContext state.
 *
 * Enables pause/resume/handoff: if a workflow is interrupted at any step,
 * the snapshot can reconstruct a RunContext with all prior step outputs,
 * allowing the pipeline to continue without re-executing completed steps.
 *
 * Content (file content) is NOT stored — it lives in git. Only metadata
 * (file paths, summary) is captured here.
 */
public record EnvironmentSnapshot(
        String owner,
        String repo,
        int issueNumber,
        Map<String, Object> issueData,
        String ticketPlan,
        String workPlan,
        String architectureNotes,
        String prUrl,
        String branchName,
        String codeSummary,
        List<String> changedFilePaths,
        String capturedAt) {

    /**
     * Capture the current state of a RunContext into a snapshot.
     */
    public static EnvironmentSnapshot of(RunContext ctx) {
        String codeSummary = null;
        List<String> changedFilePaths = List.of();

        DeveloperStep.CodeChanges codeChanges = ctx.getCodeChanges();
        if (codeChanges != null) {
            codeSummary = codeChanges.getSummary();
            if (codeChanges.getFiles() != null) {
                changedFilePaths = codeChanges.getFiles().stream()
                        .map(DeveloperStep.FileChange::getPath)
                        .collect(Collectors.toList());
            }
        }

        return new EnvironmentSnapshot(
                ctx.getOwner(),
                ctx.getRepo(),
                ctx.getRunEntity().getIssueNumber(),
                ctx.getIssueData(),
                ctx.getTicketPlan(),
                ctx.getWorkPlan(),
                ctx.getArchitectureNotes(),
                ctx.getPrUrl(),
                ctx.getBranchName(),
                codeSummary,
                changedFilePaths,
                Instant.now().toString());
    }

    /**
     * Reconstruct a RunContext from this snapshot, bound to the given RunEntity.
     * File content is not restored (lives in git); only metadata fields are set.
     */
    public RunContext restore(RunEntity entity) {
        RunContext ctx = new RunContext(entity, owner, repo);
        ctx.setIssueData(issueData);
        ctx.setTicketPlan(ticketPlan);
        ctx.setWorkPlan(workPlan);
        ctx.setArchitectureNotes(architectureNotes);
        ctx.setPrUrl(prUrl);
        ctx.setBranchName(branchName);

        if (codeSummary != null) {
            DeveloperStep.CodeChanges codeChanges = new DeveloperStep.CodeChanges();
            codeChanges.setSummary(codeSummary);
            if (changedFilePaths != null) {
                List<DeveloperStep.FileChange> fileChanges = changedFilePaths.stream()
                        .map(path -> {
                            DeveloperStep.FileChange fc = new DeveloperStep.FileChange();
                            fc.setPath(path);
                            return fc;
                        })
                        .collect(Collectors.toList());
                codeChanges.setFiles(fileChanges);
            }
            ctx.setCodeChanges(codeChanges);
        }

        return ctx;
    }
}
