package com.atlasia.ai.service;

import com.atlasia.ai.model.RunArtifactEntity;
import com.atlasia.ai.model.RunEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Builds and mutates task ledger JSON aligned with {@code task_ledger.schema.json}.
 */
@Component
public class TaskLedgerBuilder {

    private final ObjectMapper objectMapper;

    public TaskLedgerBuilder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String readLatest(RunEntity run) {
        return run.getArtifacts().stream()
                .filter(a -> "task_ledger".equals(a.getArtifactType()))
                .reduce((a, b) -> b)
                .map(RunArtifactEntity::getPayload)
                .orElse(null);
    }

    public String initialLedger(RunEntity run) throws Exception {
        int issueId = run.getIssueNumber() != null ? run.getIssueNumber() : 0;
        String now = Instant.now().toString();
        ObjectNode root = objectMapper.createObjectNode();
        root.put("correlationId", run.getId().toString());
        root.put("issueId", issueId);
        root.put("createdAt", now);
        root.put("updatedAt", now);
        root.put("status", "planning");
        root.put("current_step", "pm");

        ObjectNode loops = objectMapper.createObjectNode();
        loops.set("review_to_developer", loopPair(0, 2));
        loops.set("tester_to_developer", loopPair(0, 2));
        root.set("loop_counters", loops);

        ArrayNode steps = objectMapper.createArrayNode();
        steps.add(plannedStep(1, "PM", "pending", "ticket_plan", null));
        steps.add(plannedStep(2, "QUALIFIER", "pending", "work_plan", null));
        steps.add(plannedStep(3, "ARCHITECT", "pending", "architecture_notes", "architecture_approval"));
        steps.add(plannedStep(4, "DEVELOPER", "pending", "implementation_report", null));
        steps.add(plannedStep(5, "REVIEW", "pending", "persona_review", null));
        steps.add(plannedStep(6, "TESTER", "pending", "test_report", null));
        steps.add(plannedStep(7, "WRITER", "pending", "docs_patch", null));
        root.set("planned_steps", steps);

        return objectMapper.writeValueAsString(root);
    }

    private ObjectNode loopPair(int current, int max) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("current", current);
        n.put("max", max);
        return n;
    }

    private ObjectNode plannedStep(int step, String agent, String status, String produces, String gate) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("step", step);
        n.put("agent", agent);
        n.put("status", status);
        n.put("produces", produces);
        if (gate != null) {
            n.put("gate", gate);
        }
        return n;
    }

    public String touchUpdated(String ledgerJson) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(ledgerJson);
        root.put("updatedAt", Instant.now().toString());
        return objectMapper.writeValueAsString(root);
    }

    public String withStatus(String ledgerJson, String status, String currentStep) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(ledgerJson);
        root.put("status", status);
        root.put("current_step", currentStep);
        root.put("updatedAt", Instant.now().toString());
        return objectMapper.writeValueAsString(root);
    }

    public String withPlannedStep(String ledgerJson, String agentUpper, String newStatus,
            String startedAtOrNull, String completedAtOrNull) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(ledgerJson);
        ArrayNode steps = (ArrayNode) root.get("planned_steps");
        if (steps == null) {
            return ledgerJson;
        }
        for (int i = 0; i < steps.size(); i++) {
            ObjectNode s = (ObjectNode) steps.get(i);
            if (agentUpper.equals(s.path("agent").asText())) {
                s.put("status", newStatus);
                if (startedAtOrNull != null) {
                    s.put("started_at", startedAtOrNull);
                }
                if (completedAtOrNull != null) {
                    s.put("completed_at", completedAtOrNull);
                }
            }
        }
        root.put("updatedAt", Instant.now().toString());
        return objectMapper.writeValueAsString(root);
    }

    public String withTransition(String ledgerJson, UUID runId, String from, String to, String type,
            String reason, String gateName, int loopIteration) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(ledgerJson);
        ArrayNode transitions = (ArrayNode) root.get("transitions");
        if (transitions == null) {
            transitions = objectMapper.createArrayNode();
            root.set("transitions", transitions);
        }
        ObjectNode t = objectMapper.createObjectNode();
        t.put("from", from);
        t.put("to", to);
        t.put("type", type);
        t.put("reason", reason);
        t.put("timestamp", Instant.now().toString());
        t.put("correlationId", runId.toString());
        if (loopIteration >= 0) {
            t.put("loop_iteration", loopIteration);
        }
        if (gateName != null) {
            t.put("gate_name", gateName);
        }
        transitions.add(t);
        root.put("updatedAt", Instant.now().toString());
        return objectMapper.writeValueAsString(root);
    }

    public int getLoopCurrent(String ledgerJson, String counterKey) throws Exception {
        JsonNode root = objectMapper.readTree(ledgerJson);
        return root.path("loop_counters").path(counterKey).path("current").asInt(0);
    }

    public String incrementLoop(String ledgerJson, String counterKey, int newCurrent) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(ledgerJson);
        ObjectNode loops = (ObjectNode) root.get("loop_counters");
        if (loops == null) {
            return ledgerJson;
        }
        ObjectNode c = (ObjectNode) loops.get(counterKey);
        if (c != null) {
            c.put("current", newCurrent);
        }
        root.put("updatedAt", Instant.now().toString());
        return objectMapper.writeValueAsString(root);
    }

    public String appendGateDecision(String ledgerJson, String gateName, String transitionLabel,
            String decision, String comment) throws Exception {
        ObjectNode root = (ObjectNode) objectMapper.readTree(ledgerJson);
        ArrayNode arr = (ArrayNode) root.get("gate_decisions");
        if (arr == null) {
            arr = objectMapper.createArrayNode();
            root.set("gate_decisions", arr);
        }
        ObjectNode d = objectMapper.createObjectNode();
        d.put("gate_name", gateName);
        d.put("transition", transitionLabel);
        d.put("decision", decision);
        d.put("timestamp", Instant.now().toString());
        if (comment != null && !comment.isBlank()) {
            d.put("comment", comment);
        }
        arr.add(d);
        root.put("updatedAt", Instant.now().toString());
        return objectMapper.writeValueAsString(root);
    }
}
