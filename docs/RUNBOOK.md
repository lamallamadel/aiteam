# Escalation Analytics

## Overview
The Escalation Analyzer Service provides insights into recurring failure patterns across workflow runs by mining escalation.json artifacts.

## API Endpoint

### POST /api/analytics/escalations/insights
Triggers analysis of all escalated runs and generates an escalation insights report.

**Request:**
```bash
curl -X POST http://localhost:8080/api/analytics/escalations/insights
```

**Response:** Returns `EscalationInsightDto` with:
- `totalEscalationsAnalysed`: Count of analyzed escalated runs
- `topErrorPatterns`: Map of error pattern types to occurrence counts
- `problematicFiles`: List of files frequently appearing in escalations
- `clusters`: Categorized error clusters with root cause suggestions
- `filePathPatterns`: File patterns with frequency analysis
- `agentBottlenecks`: Agents with highest escalation rates
- `topKeywords`: Most frequent keywords from escalation messages
- `generatedAt`: Timestamp of analysis

## Generated Reports

Reports are saved to `./insights/` directory:
- `escalation_insights_<timestamp>.json`: Timestamped report
- `escalation_insights.json`: Latest report (symlink/copy)

Configure output directory via `escalation.insights.output.dir` in application.properties.

## Analysis Features

### Error Pattern Classification
Identifies common patterns:
- TIMEOUT: Slow operations, infinite loops
- COMPILATION_ERROR: Syntax errors, missing dependencies
- E2E_SELECTOR_MISSING: UI/test selector mismatches
- NULL_REFERENCE: Null pointer exceptions
- DEPENDENCY_ERROR: Import/module issues
- TEST_FAILURE: Test assertion failures
- PERMISSION_ERROR: Access control issues
- NETWORK_ERROR: Connectivity problems
- MEMORY_ERROR: Memory leaks, heap issues

### File Path Analysis
Extracts and ranks frequently problematic files from escalation contexts.

### Agent Bottleneck Detection
Identifies which agents have the highest escalation rates, helping spot training or capability gaps.

### Keyword Frequency Analysis
Surfaces common terms in escalation messages to identify recurring themes.

---
