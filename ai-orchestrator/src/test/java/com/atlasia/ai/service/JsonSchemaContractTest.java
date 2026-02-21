package com.atlasia.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonSchemaContractTest {

    private JsonSchemaValidator validator;
    private ObjectMapper objectMapper;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        objectMapper = new ObjectMapper();
        validator = new JsonSchemaValidator(objectMapper);

        createSchemaFiles();
    }

    @Test
    void ticketPlanSchema_validJson_passes() throws IOException {
        String validTicketPlan = """
                {
                    "issueId": 123,
                    "title": "Test Issue",
                    "summary": "This is a test issue",
                    "acceptanceCriteria": ["Criterion 1", "Criterion 2", "Criterion 3"],
                    "outOfScope": ["Not included"],
                    "risks": ["Risk 1"],
                    "priority": "P1",
                    "labelsToApply": ["bug", "high-priority"]
                }
                """;

        assertDoesNotThrow(() -> validator.validate(validTicketPlan, "ticket_plan.schema.json"));
    }

    @Test
    void ticketPlanSchema_missingRequiredField_fails() {
        String invalidTicketPlan = """
                {
                    "issueId": 123,
                    "title": "Test Issue"
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalidTicketPlan, "ticket_plan.schema.json"));
    }

    @Test
    void ticketPlanSchema_emptyAcceptanceCriteria_fails() {
        String invalidTicketPlan = """
                {
                    "issueId": 123,
                    "title": "Test Issue",
                    "summary": "Summary",
                    "acceptanceCriteria": [],
                    "outOfScope": [],
                    "risks": [],
                    "labelsToApply": ["bug"]
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalidTicketPlan, "ticket_plan.schema.json"));
    }

    @Test
    void workPlanSchema_validJson_passes() throws IOException {
        String validWorkPlan = """
                {
                    "branchName": "ai/issue-123-test-feature",
                    "tasks": [
                        {
                            "id": "task-1",
                            "area": "backend",
                            "description": "Implement service",
                            "filesLikely": ["src/Service.java"],
                            "tests": ["Unit tests"]
                        },
                        {
                            "id": "task-2",
                            "area": "frontend",
                            "description": "Add UI component",
                            "filesLikely": ["src/Component.ts"],
                            "tests": ["Component tests"]
                        },
                        {
                            "id": "task-3",
                            "area": "docs",
                            "description": "Update docs",
                            "filesLikely": ["README.md"],
                            "tests": []
                        }
                    ],
                    "commands": {
                        "backendVerify": "mvn verify",
                        "frontendLint": "npm run lint",
                        "frontendTest": "npm test",
                        "e2e": "npm run e2e"
                    },
                    "definitionOfDone": ["All tests pass", "PR created"]
                }
                """;

        assertDoesNotThrow(() -> validator.validate(validWorkPlan, "work_plan.schema.json"));
    }

    @Test
    void workPlanSchema_lessThanThreeTasks_fails() {
        String invalidWorkPlan = """
                {
                    "branchName": "ai/issue-123-test-feature",
                    "tasks": [
                        {
                            "id": "task-1",
                            "area": "backend",
                            "description": "Task",
                            "filesLikely": ["file.java"],
                            "tests": []
                        }
                    ],
                    "commands": {},
                    "definitionOfDone": []
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalidWorkPlan, "work_plan.schema.json"));
    }

    @Test
    void workPlanSchema_invalidArea_fails() {
        String invalidWorkPlan = """
                {
                    "branchName": "ai/issue-123-test-feature",
                    "tasks": [
                        {
                            "id": "task-1",
                            "area": "invalid-area",
                            "description": "Task",
                            "filesLikely": [],
                            "tests": []
                        },
                        {
                            "id": "task-2",
                            "area": "backend",
                            "description": "Task",
                            "filesLikely": [],
                            "tests": []
                        },
                        {
                            "id": "task-3",
                            "area": "frontend",
                            "description": "Task",
                            "filesLikely": [],
                            "tests": []
                        }
                    ],
                    "commands": {},
                    "definitionOfDone": []
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalidWorkPlan, "work_plan.schema.json"));
    }

    @Test
    void testReportSchema_validJson_passes() throws IOException {
        String validTestReport = """
                {
                    "prUrl": "https://github.com/owner/repo/pull/1",
                    "ciStatus": "GREEN",
                    "backend": {
                        "status": "PASSED",
                        "details": ["All backend tests passed"]
                    },
                    "frontend": {
                        "status": "PASSED",
                        "details": ["All frontend tests passed"]
                    },
                    "e2e": {
                        "status": "PASSED",
                        "details": ["All E2E tests passed"]
                    },
                    "notes": ["CI passed on first attempt"],
                    "timestamp": "2024-01-01T00:00:00Z"
                }
                """;

        assertDoesNotThrow(() -> validator.validate(validTestReport, "test_report.schema.json"));
    }

    @Test
    void testReportSchema_missingRequired_fails() {
        String invalidTestReport = """
                {
                    "prUrl": "https://github.com/owner/repo/pull/1"
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalidTestReport, "test_report.schema.json"));
    }

    @Test
    void escalationSchema_validJson_passes() throws IOException {
        String validEscalation = """
                {
                    "context": "Testing phase for issue #123",
                    "blocker": "CI tests failed after 3 attempts",
                    "prUrl": "https://github.com/owner/repo/pull/1",
                    "branchName": "ai/issue-123-test-feature",
                    "ciFixAttempts": 3,
                    "e2eFixAttempts": 0,
                    "evidence": ["Test failure logs", "Error messages"],
                    "detailedFailures": ["Test class failed with assertion error"],
                    "timestamp": "2024-01-01T00:00:00Z",
                    "options": [
                        {
                            "name": "Manual intervention",
                            "pros": ["Can fix complex issues"],
                            "cons": ["Takes time"],
                            "risk": "LOW"
                        },
                        {
                            "name": "Retry",
                            "pros": ["Automated"],
                            "cons": ["May fail again"],
                            "risk": "MEDIUM"
                        }
                    ],
                    "recommendation": "Manual intervention",
                    "decisionNeeded": "How to proceed with test failures"
                }
                """;

        assertDoesNotThrow(() -> validator.validate(validEscalation, "escalation.schema.json"));
    }

    @Test
    void escalationSchema_missingOptions_fails() {
        String invalidEscalation = """
                {
                    "context": "Testing phase",
                    "blocker": "Tests failed",
                    "evidence": [],
                    "recommendation": "Manual intervention",
                    "decisionNeeded": "What to do"
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalidEscalation, "escalation.schema.json"));
    }

    @Test
    void validator_invalidJson_throwsException() {
        String invalidJson = "{ invalid json }";

        assertThrows(Exception.class,
                () -> validator.validate(invalidJson, "ticket_plan.schema.json"));
    }

    @Test
    void validator_nonExistentSchema_throwsException() {
        String validJson = "{\"test\": \"value\"}";

        assertThrows(IOException.class,
                () -> validator.validate(validJson, "nonexistent.schema.json"));
    }

    @Test
    void ticketPlanSchema_additionalProperties_fails() {
        String invalidTicketPlan = """
                {
                    "issueId": 123,
                    "title": "Test",
                    "summary": "Test",
                    "acceptanceCriteria": ["Test"],
                    "outOfScope": [],
                    "risks": [],
                    "labelsToApply": ["bug"],
                    "extraField": "not allowed"
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalidTicketPlan, "ticket_plan.schema.json"));
    }

    @Test
    void workPlanSchema_taskWithoutRequiredFields_fails() {
        String invalidWorkPlan = """
                {
                    "branchName": "ai/issue-123-test-feature",
                    "tasks": [
                        {
                            "id": "task-1",
                            "area": "backend"
                        },
                        {
                            "id": "task-2",
                            "area": "frontend",
                            "description": "Task",
                            "filesLikely": [],
                            "tests": []
                        },
                        {
                            "id": "task-3",
                            "area": "docs",
                            "description": "Task",
                            "filesLikely": [],
                            "tests": []
                        }
                    ],
                    "commands": {},
                    "definitionOfDone": []
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalidWorkPlan, "work_plan.schema.json"));
    }

    @Test
    void testReportSchema_invalidStatus_fails() {
        String invalidTestReport = """
                {
                    "prUrl": "https://github.com/owner/repo/pull/1",
                    "ciStatus": "INVALID_STATUS",
                    "backend": {"status": "PASSED", "details": []},
                    "frontend": {"status": "PASSED", "details": []},
                    "e2e": {"status": "PASSED", "details": []},
                    "notes": [],
                    "timestamp": "2024-01-01T00:00:00Z"
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> validator.validate(invalidTestReport, "test_report.schema.json"));
    }

    @Test
    void escalationSchema_allRequiredFields_passes() throws IOException {
        String validEscalation = """
                {
                    "context": "Test context",
                    "blocker": "Test blocker",
                    "evidence": ["Evidence 1"],
                    "options": [
                        {
                            "name": "Option 1",
                            "pros": ["Pro 1"],
                            "cons": ["Con 1"],
                            "risk": "LOW"
                        },
                        {
                            "name": "Option 2",
                            "pros": ["Pro 2"],
                            "cons": ["Con 2"],
                            "risk": "MEDIUM"
                        }
                    ],
                    "recommendation": "Recommendation",
                    "decisionNeeded": "Decision needed"
                }
                """;

        assertDoesNotThrow(() -> validator.validate(validEscalation, "escalation.schema.json"));
    }

    // Helper method to create schema files in temp directory
    private void createSchemaFiles() throws IOException {
        Path schemasDir = tempDir.resolve("ai").resolve("schemas");
        Files.createDirectories(schemasDir);

        createTicketPlanSchema(schemasDir);
        createWorkPlanSchema(schemasDir);
        createTestReportSchema(schemasDir);
        createEscalationSchema(schemasDir);

        System.setProperty("user.dir", tempDir.toString());
    }

    private void createTicketPlanSchema(Path schemasDir) throws IOException {
        String schema = """
                {
                    "$schema": "http://json-schema.org/draft-07/schema#",
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["issueId", "title", "summary", "acceptanceCriteria", "outOfScope", "risks", "labelsToApply"],
                    "properties": {
                        "issueId": {"type": "integer"},
                        "title": {"type": "string"},
                        "summary": {"type": "string"},
                        "acceptanceCriteria": {
                            "type": "array",
                            "items": {"type": "string"},
                            "minItems": 1
                        },
                        "outOfScope": {
                            "type": "array",
                            "items": {"type": "string"}
                        },
                        "risks": {
                            "type": "array",
                            "items": {"type": "string"}
                        },
                        "labelsToApply": {
                            "type": "array",
                            "items": {"type": "string"},
                            "minItems": 1
                        }
                    }
                }
                """;
        Files.writeString(schemasDir.resolve("ticket_plan.schema.json"), schema);
    }

    private void createWorkPlanSchema(Path schemasDir) throws IOException {
        String schema = """
                {
                    "$schema": "http://json-schema.org/draft-07/schema#",
                    "type": "object",
                    "additionalProperties": false,
                    "required": ["tasks", "definitionOfDone"],
                    "properties": {
                        "branchName": {"type": "string"},
                        "tasks": {
                            "type": "array",
                            "minItems": 3,
                            "items": {
                                "type": "object",
                                "required": ["id", "area", "description", "filesLikely", "tests"],
                                "additionalProperties": false,
                                "properties": {
                                    "id": {"type": "string"},
                                    "area": {
                                        "type": "string",
                                        "enum": ["backend", "frontend", "infra", "docs"]
                                    },
                                    "description": {"type": "string"},
                                    "filesLikely": {
                                        "type": "array",
                                        "items": {"type": "string"}
                                    },
                                    "tests": {
                                        "type": "array",
                                        "items": {"type": "string"}
                                    }
                                }
                            }
                        },
                        "commands": {"type": "object"},
                        "definitionOfDone": {
                            "type": "array",
                            "items": {"type": "string"}
                        }
                    }
                }
                """;
        Files.writeString(schemasDir.resolve("work_plan.schema.json"), schema);
    }

    private void createTestReportSchema(Path schemasDir) throws IOException {
        String schema = """
                {
                    "$schema": "http://json-schema.org/draft-07/schema#",
                    "type": "object",
                    "required": ["prUrl", "ciStatus", "backend", "frontend", "e2e", "notes", "timestamp"],
                    "properties": {
                        "prUrl": {"type": "string"},
                        "ciStatus": {
                            "type": "string",
                            "enum": ["GREEN", "YELLOW", "RED"]
                        },
                        "backend": {
                            "type": "object",
                            "required": ["status", "details"],
                            "properties": {
                                "status": {"type": "string"},
                                "details": {
                                    "type": "array",
                                    "items": {"type": "string"}
                                }
                            }
                        },
                        "frontend": {
                            "type": "object",
                            "required": ["status", "details"],
                            "properties": {
                                "status": {"type": "string"},
                                "details": {
                                    "type": "array",
                                    "items": {"type": "string"}
                                }
                            }
                        },
                        "e2e": {
                            "type": "object",
                            "required": ["status", "details"],
                            "properties": {
                                "status": {"type": "string"},
                                "details": {
                                    "type": "array",
                                    "items": {"type": "string"}
                                }
                            }
                        },
                        "notes": {
                            "type": "array",
                            "items": {"type": "string"}
                        },
                        "timestamp": {"type": "string"}
                    }
                }
                """;
        Files.writeString(schemasDir.resolve("test_report.schema.json"), schema);
    }

    private void createEscalationSchema(Path schemasDir) throws IOException {
        String schema = """
                {
                    "$schema": "http://json-schema.org/draft-07/schema#",
                    "type": "object",
                    "required": ["context", "blocker", "evidence", "options", "recommendation", "decisionNeeded"],
                    "properties": {
                        "context": {"type": "string"},
                        "blocker": {"type": "string"},
                        "prUrl": {"type": "string"},
                        "branchName": {"type": "string"},
                        "ciFixAttempts": {"type": "integer"},
                        "e2eFixAttempts": {"type": "integer"},
                        "evidence": {
                            "type": "array",
                            "items": {"type": "string"}
                        },
                        "detailedFailures": {
                            "type": "array",
                            "items": {"type": "string"}
                        },
                        "timestamp": {"type": "string"},
                        "options": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "required": ["name", "pros", "cons", "risk"],
                                "properties": {
                                    "name": {"type": "string"},
                                    "pros": {
                                        "type": "array",
                                        "items": {"type": "string"}
                                    },
                                    "cons": {
                                        "type": "array",
                                        "items": {"type": "string"}
                                    },
                                    "risk": {"type": "string"}
                                }
                            }
                        },
                        "recommendation": {"type": "string"},
                        "decisionNeeded": {"type": "string"}
                    }
                }
                """;
        Files.writeString(schemasDir.resolve("escalation.schema.json"), schema);
    }
}
