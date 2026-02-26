# Audit Trail and Compliance Reporting System

## Overview

The Atlasia AI Orchestrator includes a comprehensive audit trail and compliance reporting system designed to meet GDPR, SOC2, and ISO 27001 requirements. The system uses blockchain-inspired tamper-proof logging with SHA-256 hash chains to ensure data integrity.

## Features

### 1. Tamper-Proof Audit Trail

All audit events are linked in a hash chain, where each event contains:
- **Event Hash**: SHA-256 hash of the event's core data
- **Previous Event Hash**: Hash of the previous event in the chain
- **Retention Policy**: Configurable retention period (default: 2555 days / ~7 years)
- **Archive Timestamp**: When the event was archived

Event types tracked:
- **Authentication Events**: Login, logout, MFA, password changes
- **Access Logs**: API access, resource access, authorization decisions
- **Data Mutations**: Create, update, delete operations on entities
- **Admin Actions**: User management, permission changes, system configuration
- **Collaboration Events**: Workflow grafts, prunes, flags

### 2. GDPR Compliance

#### User Data Export

Users can request a complete export of their personal data via:

```
GET /api/compliance/export?userId={userId}
```

**Response includes:**
- User profile information
- Authentication history
- Access logs
- Data mutations
- Admin actions (as target or admin)
- Collaboration events

**Access Control:**
- Users can export their own data
- Admins can export any user's data

Example request:
```bash
curl -X GET "https://api.atlasia.ai/api/compliance/export?userId=123e4567-e89b-12d3-a456-426614174000" \
  -H "Authorization: Bearer {token}"
```

Example response:
```json
{
  "exportTimestamp": "2024-01-15T10:30:00Z",
  "userId": "123e4567-e89b-12d3-a456-426614174000",
  "user": {
    "username": "john.doe",
    "email": "john.doe@example.com",
    "createdAt": "2023-01-01T00:00:00Z",
    "enabled": true,
    "mfaEnabled": true
  },
  "authenticationEvents": [...],
  "accessLogs": [...],
  "dataMutations": [...],
  "adminActionsAsTarget": [...],
  "adminActionsAsAdmin": [...],
  "collaborationEvents": [...],
  "totalRecords": 1523
}
```

### 3. SOC2 Compliance Reports

Automated generation of SOC2 Trust Services Criteria compliance evidence.

#### Manual Generation (Admin Only)

```
POST /api/compliance/reports/soc2?periodStart={ISO-8601}&periodEnd={ISO-8601}
```

Example:
```bash
curl -X POST "https://api.atlasia.ai/api/compliance/reports/soc2?periodStart=2024-01-01T00:00:00Z&periodEnd=2024-01-31T23:59:59Z" \
  -H "Authorization: Bearer {admin-token}"
```

#### Automated Scheduled Reports

- **Monthly Reports**: 1st of every month at 3:00 AM
- **Quarterly Reports**: 1st of every quarter at 4:00 AM

**CSV Format:**
```csv
Event Type,Timestamp,User,Resource,Action,Status,IP Address,Details
AUTHENTICATION,2024-01-15T10:30:00Z,john.doe,USER_ACCOUNT,LOGIN,SUCCESS,192.168.1.100,
ACCESS_LOG,2024-01-15T10:31:00Z,john.doe,RUN/uuid,READ,200,192.168.1.100,/api/runs/uuid
DATA_MUTATION,2024-01-15T10:32:00Z,john.doe,RUN/uuid,UPDATE,N/A,N/A,status
ADMIN_ACTION,2024-01-15T10:33:00Z,admin,USER/john.doe,GRANT_PERMISSION,N/A,192.168.1.50,Added ROLE_DEVELOPER
```

### 4. ISO 27001 Compliance Reports

Automated generation of ISO 27001 control evidence mapped to specific control domains.

#### Manual Generation (Admin Only)

```
POST /api/compliance/reports/iso27001?periodStart={ISO-8601}&periodEnd={ISO-8601}
```

#### Automated Scheduled Reports

- **Monthly Reports**: 1st of every month at 3:30 AM
- **Quarterly Reports**: 1st of every quarter at 4:30 AM

**CSV Format with Control Mapping:**
```csv
Control Domain,Event Type,Timestamp,User,Action,Resource,Status,Evidence
A.9.4.2 Access Control,AUTHENTICATION,2024-01-15T10:30:00Z,john.doe,LOGIN,Authentication System,SUCCESS,IP: 192.168.1.100
A.9.4.1 Information Access Restriction,ACCESS_LOG,2024-01-15T10:31:00Z,john.doe,READ,RUN/uuid,HTTP 200,/api/runs/uuid
A.12.4.1 Event Logging,DATA_MUTATION,2024-01-15T10:32:00Z,john.doe,UPDATE,RUN/uuid,LOGGED,Field: status
A.9.2.3 Privileged Access Management,ADMIN_ACTION,2024-01-15T10:33:00Z,admin,GRANT_PERMISSION,Target: john.doe,EXECUTED,Added ROLE_DEVELOPER
```

## Database Schema

### Audit Tables

#### `audit_authentication_events`
- Authentication events (login, logout, MFA)
- Tracks success/failure, IP address, user agent
- Hash chain for tamper detection

#### `audit_access_logs`
- API and resource access logs
- HTTP method, endpoint, status code
- Resource type and ID tracking

#### `audit_data_mutations`
- Data change tracking
- Entity type, operation (CREATE/UPDATE/DELETE)
- Field-level old/new value tracking

#### `audit_admin_actions`
- Administrative actions
- Target user tracking
- Detailed action descriptions

#### `compliance_reports`
- Report generation tracking
- Report type, period, status
- File path and record count

### Hash Chain Implementation

Each audit event includes:

```sql
previous_event_hash VARCHAR(64)  -- Hash of previous event
event_hash VARCHAR(64)           -- SHA-256 hash of current event
```

Hash computation:
```
event_hash = SHA256(
  previous_hash + "|" +
  username + "|" +
  event_type + "|" +
  timestamp + "|" +
  [event-specific-data]
)
```

### Retention Policy

Default retention: **2555 days** (~7 years)

Events are soft-archived (not deleted) when retention period expires:
```sql
archived_at TIMESTAMP  -- When event was archived
```

## Service Architecture

### `AuditTrailService`

Core service for tamper-proof logging:

```java
// Log authentication event
auditTrailService.logAuthenticationEvent(
  userId, username, "LOGIN", 
  ipAddress, userAgent, true, null
);

// Log access event
auditTrailService.logAccessEvent(
  userId, username, "RUN", runId.toString(),
  "READ", "GET", "/api/runs/" + runId,
  ipAddress, userAgent, 200
);

// Log data mutation
auditTrailService.logDataMutation(
  userId, username, "RUN", runId.toString(),
  "UPDATE", "status", "RUNNING", "COMPLETED"
);

// Log admin action
auditTrailService.logAdminAction(
  adminUserId, adminUsername, "GRANT_PERMISSION",
  targetUserId, targetUsername, "Added ROLE_DEVELOPER",
  ipAddress
);

// Verify hash chain integrity
boolean isValid = auditTrailService.verifyHashChain("authentication");
```

### `ComplianceReportService`

Handles GDPR exports and compliance report generation:

```java
// GDPR export
Map<String, Object> userData = 
  complianceReportService.exportUserDataForGDPR(userId);

// Generate SOC2 report
String filePath = complianceReportService.generateSOC2Report(
  periodStart, periodEnd, username
);

// Generate ISO 27001 report
String filePath = complianceReportService.generateISO27001Report(
  periodStart, periodEnd, username
);
```

### `AuditReportScheduler`

Automated report generation using Spring's `@Scheduled`:

- Monthly SOC2: `0 0 3 1 * ?` (3:00 AM on 1st of month)
- Monthly ISO 27001: `0 30 3 1 * ?` (3:30 AM on 1st of month)
- Quarterly SOC2: `0 0 4 1 */3 ?` (4:00 AM on 1st of quarter)
- Quarterly ISO 27001: `0 30 4 1 */3 ?` (4:30 AM on 1st of quarter)

## Integration Guide

### 1. Enable Audit Logging in Your Service

```java
@Service
public class MyService {
    private final AuditTrailService auditTrailService;
    
    public void performAction(User user, String action) {
        // Your business logic
        
        // Log the action
        auditTrailService.logAccessEvent(
            user.getId(), user.getUsername(),
            "MY_RESOURCE", resourceId,
            action, "POST", "/api/my-resource",
            request.getRemoteAddr(), request.getHeader("User-Agent"),
            200
        );
    }
}
```

### 2. Log Authentication Events

```java
@Service
public class AuthenticationService {
    private final AuditTrailService auditTrailService;
    
    public void onLoginSuccess(User user, HttpServletRequest request) {
        auditTrailService.logAuthenticationEvent(
            user.getId(), user.getUsername(), "LOGIN",
            request.getRemoteAddr(), request.getHeader("User-Agent"),
            true, null
        );
    }
    
    public void onLoginFailure(String username, String reason, 
                              HttpServletRequest request) {
        auditTrailService.logAuthenticationEvent(
            null, username, "LOGIN",
            request.getRemoteAddr(), request.getHeader("User-Agent"),
            false, reason
        );
    }
}
```

### 3. Log Data Mutations

```java
@Service
public class RunService {
    private final AuditTrailService auditTrailService;
    
    @Transactional
    public void updateRunStatus(UUID runId, String newStatus, User user) {
        Run run = runRepository.findById(runId).orElseThrow();
        String oldStatus = run.getStatus();
        
        run.setStatus(newStatus);
        runRepository.save(run);
        
        // Audit the change
        auditTrailService.logDataMutation(
            user.getId(), user.getUsername(),
            "RUN", runId.toString(),
            "UPDATE", "status", oldStatus, newStatus
        );
    }
}
```

## Security Considerations

### Hash Chain Integrity

The hash chain provides:
- **Tamper Detection**: Any modification breaks the chain
- **Chronological Order**: Events are cryptographically linked
- **Non-Repudiation**: Cannot deny logged actions

### Verification

Periodic verification of hash chains:

```java
@Scheduled(cron = "0 0 2 * * ?") // Daily at 2 AM
public void verifyAuditIntegrity() {
    boolean authValid = auditTrailService.verifyHashChain("authentication");
    boolean accessValid = auditTrailService.verifyHashChain("access_logs");
    boolean mutationValid = auditTrailService.verifyHashChain("data_mutations");
    boolean adminValid = auditTrailService.verifyHashChain("admin_actions");
    
    if (!authValid || !accessValid || !mutationValid || !adminValid) {
        // Alert security team
        logger.error("SECURITY ALERT: Audit trail integrity violation detected");
    }
}
```

### Access Control

- **GDPR Exports**: Users can access their own data, admins can access any user's data
- **Compliance Reports**: Admin role required
- **Audit Logs**: Append-only, no deletion (only archival after retention period)

## File Storage

Compliance reports are stored in:
```
compliance-reports/
├── soc2_report_2024-01-01_2024-01-31.csv
├── soc2_report_2024-02-01_2024-02-29.csv
├── iso27001_report_2024-01-01_2024-01-31.csv
└── iso27001_report_2024-02-01_2024-02-29.csv
```

This directory is excluded from git via `.gitignore`.

## Monitoring and Alerting

### Key Metrics

- Audit event ingestion rate
- Hash chain verification results
- Compliance report generation success/failure
- GDPR export request volume

### Recommended Alerts

1. **Hash Chain Verification Failure**: Critical alert to security team
2. **Compliance Report Generation Failure**: Alert to compliance team
3. **High Volume of GDPR Exports**: Potential data breach indicator
4. **Audit Event Write Failures**: System integrity issue

## Compliance Checklist

### GDPR (General Data Protection Regulation)

- ✅ Right to Access: `/api/compliance/export` endpoint
- ✅ Right to Portability: JSON export format
- ✅ Right to Erasure: Retention policy with archival
- ✅ Data Breach Notification: Audit trail for detection
- ✅ Accountability: Tamper-proof logging

### SOC2 (System and Organization Controls)

- ✅ CC6.1: Logical and Physical Access Controls (access logs)
- ✅ CC6.2: Authentication and Authorization (auth events)
- ✅ CC6.3: System Monitoring (audit trail)
- ✅ CC7.2: System Operation (admin actions)
- ✅ CC7.4: Data Backup and Retention (retention policy)

### ISO 27001

- ✅ A.9.2.3: Privileged Access Management (admin actions)
- ✅ A.9.4.1: Information Access Restriction (access logs)
- ✅ A.9.4.2: Access Control (authentication events)
- ✅ A.12.4.1: Event Logging (all audit events)
- ✅ A.12.4.3: Administrator and Operator Logs (admin actions)

## Troubleshooting

### Hash Chain Verification Failure

If hash chain verification fails:

1. Check database for direct modifications
2. Review database migration logs
3. Inspect the specific event where chain breaks
4. Alert security team immediately

### Report Generation Failure

If automated reports fail:

1. Check disk space in `compliance-reports/` directory
2. Review scheduler logs for exceptions
3. Verify database connectivity
4. Check file system permissions

### Missing Audit Events

If audit events are missing:

1. Verify `AuditTrailService` is properly injected
2. Check for transaction rollbacks
3. Review application logs for exceptions
4. Ensure database migrations have run

## Future Enhancements

- [ ] Blockchain integration for distributed verification
- [ ] Real-time audit event streaming to SIEM
- [ ] Automated anomaly detection in audit logs
- [ ] Compliance report signing with digital signatures
- [ ] Multi-region audit log replication
- [ ] Advanced retention policies with legal hold support
