# PmStep LLM Intelligence Implementation

## Overview

The `PmStep` class has been enhanced with comprehensive LLM intelligence to analyze GitHub issues and generate structured ticket plans. This implementation combines LLM capabilities with advanced NLP techniques and robust fallback strategies.

## Key Features

### 1. GitHub Issue Parsing

The implementation parses GitHub issues to extract:
- **Issue Title and Body**: Core requirements and description
- **Labels**: Existing categorization and metadata
- **Comments**: Additional context and clarifications from team members

Implementation in `execute()` method:
```java
Map<String, Object> issueData = gitHubApiClient.readIssue(...);
List<Map<String, Object>> comments = fetchIssueComments(context);
```

### 2. LLM Prompt Construction

The system constructs comprehensive prompts for the LLM:

#### System Prompt
Instructs the LLM to act as a product manager focusing on:
- Understanding core requirements and user needs
- Identifying clear, testable acceptance criteria
- Recognizing scope boundaries
- Identifying risks, challenges, and dependencies
- Suggesting appropriate labels

#### User Prompt
Includes complete context:
- Issue number and title
- Full issue body
- Existing labels
- Up to 10 relevant comments (truncated to 300 chars each)

### 3. Structured Schema Definition

The `buildTicketPlanSchema()` method defines a strict JSON schema with:
- `issueId` (integer): GitHub issue number
- `title` (string): Issue title
- `summary` (string): 2-4 sentence summary
- `acceptanceCriteria` (array): Specific, testable criteria
- `outOfScope` (array): Explicitly excluded items
- `risks` (array): Potential challenges and blockers
- `labelsToApply` (array): Suggested labels for categorization

### 4. LLM Response Validation

The `parseAndValidateLlmResponse()` method:
- Cleans markdown code blocks (```json)
- Parses JSON response into `TicketPlan` object
- Validates issue ID and title consistency
- Handles parsing errors gracefully

### 5. NLP Extraction Techniques

#### Acceptance Criteria Extraction (`extractAcceptanceCriteriaWithNlp`)
- **Section Pattern Matching**: Detects "Acceptance Criteria", "AC", "Success Criteria", "Definition of Done", etc.
- **Checkbox Detection**: Extracts items with `[ ]` or `[x]` markers
- **Imperative Pattern**: Finds statements with "should", "must", "needs to", "will", "shall"
- **BDD Format**: Detects Given/When/Then patterns
- **Similarity Filtering**: Uses Levenshtein distance to avoid duplicates

#### Out of Scope Extraction (`extractOutOfScopeWithNlp`)
- **Section Detection**: Identifies "Out of Scope", "Not in Scope", "Future Work" sections
- **Negative Patterns**: Finds "will not", "won't", "should not", "not included"
- **Future Work**: Detects "future", "later", "next version", "deferred"

#### Risk Extraction (`extractRisksWithNlp`)
- **Risk Sections**: Identifies "Risks", "Challenges", "Concerns", "Blockers", "Dependencies"
- **Risk Indicators**: Patterns like "might", "may", "could", "risk of", "depends on"
- **Keyword Detection**: 
  - Complexity: "complex", "complicated", "difficult", "challenging"
  - Performance: "performance", "slow", "latency", "scalability"
  - Security: "security", "vulnerability", "authentication", "encryption"
  - Breaking Changes: "breaking change", "backwards compatibility", "migration"

### 6. Label Suggestion (`suggestLabels`)

Automatic label suggestion based on keywords:
- **bug**: "bug", "error", "issue", "broken", "fix", "crash"
- **enhancement**: "feature", "enhancement", "add", "new", "implement"
- **documentation**: "doc", "documentation", "readme", "guide"
- **testing**: "test", "testing", "spec", "e2e", "integration"
- **refactoring**: "refactor", "cleanup", "improve", "optimize"
- **security**: "security", "vulnerability", "auth", "encrypt"
- **high-priority**: "urgent", "critical", "blocker", "asap"
- **backend**: "backend", "api", "server", "database"
- **frontend**: "frontend", "ui", "ux", "interface"
- **infrastructure**: "infrastructure", "deploy", "ci", "docker"

### 7. Fallback Strategy

When LLM fails (network error, timeout, invalid response), the system:
1. Logs the error for monitoring
2. Uses pure NLP extraction techniques
3. Generates a complete `TicketPlan` with:
   - Summary from issue body
   - Acceptance criteria via NLP patterns
   - Out-of-scope items via NLP patterns
   - Risks via NLP patterns
   - Labels via keyword heuristics

Implementation in `generateFallbackTicketPlan()` method.

### 8. Plan Enhancement

The `validateAndEnhancePlan()` method ensures completeness:
- Fills missing `issueId` and `title`
- Generates fallback summary if missing
- Enhances LLM criteria with NLP-extracted items
- Adds NLP-extracted out-of-scope items if empty
- Supplements risks with NLP patterns
- Enhances labels with keyword-based suggestions

## Advanced Techniques

### Similarity Detection
Uses Levenshtein distance algorithm to:
- Avoid duplicate criteria/risks
- Calculate string similarity (>85% threshold)
- Normalize and compare strings

### Text Cleaning
- Removes markdown code blocks
- Strips HTML comments
- Normalizes whitespace
- Extracts list items (bullets, numbered)

### Comment Processing
- Filters bot comments (starting with `<!--`)
- Limits to 10 most relevant comments
- Truncates long comments (300 char max)
- Includes author information

## Error Handling

The implementation includes comprehensive error handling:
1. **LLM Failures**: Automatic fallback to NLP
2. **JSON Parse Errors**: Logged and triggers fallback
3. **Label Addition Failures**: Warning logged, execution continues
4. **Comment Fetch Failures**: Returns empty list, execution continues
5. **Empty/Missing Fields**: NLP enhancement fills gaps

## Testing

Comprehensive unit tests in `PmStepTest.java` cover:
- Valid LLM responses
- LLM failures and fallback
- Comment integration
- Explicit acceptance criteria extraction
- Out-of-scope extraction
- Risk extraction
- Label suggestion
- Markdown code block cleaning
- Checkbox item extraction
- Empty body handling
- Label addition failures
- Given/When/Then format

## Dependencies

- **GitHubApiClient**: Fetches issue data and comments
- **LlmService**: Generates structured LLM output
- **ObjectMapper**: JSON serialization/deserialization

## Future Enhancements

Potential improvements:
1. Multi-language support for NLP patterns
2. Machine learning for label classification
3. Priority scoring based on issue content
4. Time estimation based on complexity
5. Dependency graph extraction
6. Stakeholder identification
7. Related issue linking
