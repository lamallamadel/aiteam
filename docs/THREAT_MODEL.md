# STRIDE Threat Model

**Last Updated:** 2026-02-21  
**Owner:** Security Team  
**Review Cycle:** Quarterly  
**Version:** 1.0

---

## Table of Contents

1. [Overview](#overview)
2. [System Architecture](#system-architecture)
3. [STRIDE Analysis](#stride-analysis)
4. [Attack Scenarios](#attack-scenarios)
5. [Security Controls Matrix](#security-controls-matrix)
6. [Threat Prioritization](#threat-prioritization)
7. [Residual Risks](#residual-risks)

---

## Overview

This document provides a comprehensive STRIDE threat model for the Atlasia AI Orchestrator platform. STRIDE is a threat modeling methodology that categorizes security threats into six categories:

- **S**poofing Identity
- **T**ampering with Data
- **R**epudiation
- **I**nformation Disclosure
- **D**enial of Service
- **E**levation of Privilege

### Scope

The threat model covers:
- Spring Boot backend (ai-orchestrator)
- Angular frontend
- PostgreSQL database
- HashiCorp Vault
- WebSocket collaboration system
- File upload/artifact storage
- Container runtime environment

---

## System Architecture

### Component Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  User Browser                                                   │
│  - Angular SPA                                                  │
│  - WebSocket Client                                             │
│  - JWT Token Storage                                            │
└─────────────┬───────────────────────────────────────────────────┘
              │ HTTPS/WSS
              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Application Layer (Spring Boot)                                │
│  - REST Controllers (JWT Auth)                                  │
│  - WebSocket Endpoints (STOMP/SockJS)                          │
│  - Rate Limiting (10 req/min for downloads)                    │
│  - Input Validation & Sanitization                             │
│  - RBAC/ABAC Authorization                                     │
└─────────────┬───────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Business Logic Layer                                           │
│  - Workflow Engine                                              │
│  - LLM Service (API calls)                                      │
│  - OAuth2 Service (GitHub/Google/GitLab)                       │
│  - Collaboration Service (real-time mutations)                  │
│  - File Upload Security Service                                │
│  - Audit Logging                                                │
└─────────────┬───────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Data Layer                                                      │
│  - PostgreSQL (TLS 1.3)                                         │
│  - Column Encryption (AES-256-GCM)                              │
│  - Database Constraints                                         │
│  - Audit Tables (collaboration_events, auth events)            │
└─────────────────────────────────────────────────────────────────┘
              │
              ↓
┌─────────────────────────────────────────────────────────────────┐
│  Secrets Management (HashiCorp Vault)                           │
│  - JWT Signing Keys                                             │
│  - OAuth2 Credentials                                           │
│  - Encryption Keys                                              │
│  - Database Passwords                                           │
│  - API Keys                                                     │
└─────────────────────────────────────────────────────────────────┘
```

### Data Flow

1. **Authentication Flow**: User → Login → JWT Token → Vault (JWT signing key) → Response
2. **Workflow Execution**: User → Create Run → Workflow Engine → LLM API → Database
3. **Real-time Collaboration**: User A → WebSocket → Collaboration Service → User B
4. **File Upload**: User → Upload Artifact → Security Validation → Filesystem → Database Metadata
5. **OAuth2 Flow**: User → OAuth Provider → Callback → Token Exchange → Database (encrypted)

---

## STRIDE Analysis

### Component 1: Authentication & Authorization

#### S - Spoofing Identity

**Threats:**
- **T1.1**: Attacker steals JWT access token from local storage
- **T1.2**: Attacker steals refresh token and reuses it
- **T1.3**: Attacker compromises OAuth2 access token (GitHub/Google/GitLab)
- **T1.4**: Session hijacking via network interception
- **T1.5**: Credential stuffing attacks using leaked passwords

**Mitigations:**
- **M1.1**: Short JWT TTL (15 minutes) limits token theft impact
- **M1.2**: Refresh token rotation - single use tokens prevent replay
- **M1.3**: Refresh tokens hashed (SHA-256) before storage
- **M1.4**: OAuth2 tokens encrypted at rest (AES-256-GCM)
- **M1.5**: HTTPS/TLS 1.3 only - prevents network interception
- **M1.6**: BCrypt password hashing (strength 12)
- **M1.7**: Brute force protection (5 failed attempts → account lockout)
- **M1.8**: JWT signature validation using HMAC-SHA512
- **M1.9**: Secure token storage in `httpOnly` cookies (recommended over localStorage)
- **M1.10**: Device fingerprinting in refresh tokens

**Residual Risk**: MEDIUM - Malware on user device could still extract tokens from memory/localStorage

#### T - Tampering with Data

**Threats:**
- **T1.6**: Attacker modifies JWT payload (elevate privileges)
- **T1.7**: Attacker tampers with OAuth2 authorization code
- **T1.8**: Attacker modifies user roles in database
- **T1.9**: Man-in-the-middle attack modifies authentication data

**Mitigations:**
- **M1.11**: JWT signature validation - modified tokens rejected
- **M1.12**: OAuth2 state parameter validation - CSRF protection
- **M1.13**: Database integrity constraints on user_roles table
- **M1.14**: TLS 1.3 with AEAD ciphers - prevents MITM tampering
- **M1.15**: Request signature validation for critical operations
- **M1.16**: Audit logging of all role/permission changes
- **M1.17**: Role changes require admin authentication with MFA (future)

**Residual Risk**: LOW - Strong cryptographic controls in place

#### R - Repudiation

**Threats:**
- **T1.10**: User denies performing authentication action
- **T1.11**: Admin denies changing user permissions
- **T1.12**: OAuth2 account linking denied by user

**Mitigations:**
- **M1.18**: Audit logging of all authentication events (login, logout, token refresh)
- **M1.19**: Audit logging of OAuth2 account linking/unlinking
- **M1.20**: Audit logging of permission/role changes with admin user ID
- **M1.21**: Immutable audit logs (append-only tables)
- **M1.22**: Timestamps with timezone for all audit events
- **M1.23**: IP address and user agent logging
- **M1.24**: Device info tracking in refresh tokens

**Residual Risk**: LOW - Comprehensive audit trail

#### I - Information Disclosure

**Threats:**
- **T1.13**: JWT token exposes sensitive user information
- **T1.14**: OAuth2 tokens exposed in logs
- **T1.15**: Database dump exposes passwords or OAuth tokens
- **T1.16**: Refresh tokens exposed in database backup
- **T1.17**: Session tokens visible in URL parameters
- **T1.18**: JWT signing key exposed in application config

**Mitigations:**
- **M1.25**: JWT contains minimal claims (userId, username, roles, permissions only)
- **M1.26**: No PII in JWT payload
- **M1.27**: OAuth2 access/refresh tokens encrypted at rest (AES-256-GCM)
- **M1.28**: Passwords BCrypt hashed (irreversible)
- **M1.29**: Refresh tokens SHA-256 hashed before storage
- **M1.30**: JWT signing key stored in Vault, not config files
- **M1.31**: Token values masked in logs (e.g., "eyJhbG...truncated")
- **M1.32**: HTTPS only - no tokens in URL parameters
- **M1.33**: Database column encryption for OAuth tokens
- **M1.34**: Encrypted database backups
- **M1.35**: Secrets scanning in CI/CD (Trivy)

**Residual Risk**: MEDIUM - Database administrator could decrypt tokens with encryption key

#### D - Denial of Service

**Threats:**
- **T1.19**: Attacker floods login endpoint with requests
- **T1.20**: Attacker attempts credential stuffing at scale
- **T1.21**: Refresh token exhaustion attack
- **T1.22**: OAuth2 callback flood

**Mitigations:**
- **M1.36**: Rate limiting on login endpoint (configurable)
- **M1.37**: Brute force protection (5 failed attempts → lockout)
- **M1.38**: Refresh token cleanup job (daily, removes expired tokens)
- **M1.39**: OAuth2 state validation (prevents replay)
- **M1.40**: Connection pooling limits for database
- **M1.41**: Circuit breakers for external OAuth providers
- **M1.42**: Resource limits on containers (CPU: 2 cores, Memory: 2GB)
- **M1.43**: Database connection limits (max pool size)

**Residual Risk**: MEDIUM - Distributed DoS could still overwhelm infrastructure

#### E - Elevation of Privilege

**Threats:**
- **T1.23**: Attacker modifies JWT to add admin role
- **T1.24**: SQL injection in authentication query grants admin access
- **T1.25**: Privilege escalation via OAuth2 scope manipulation
- **T1.26**: Container breakout gains root on host

**Mitigations:**
- **M1.44**: JWT signature validation prevents modification
- **M1.45**: JPA/Hibernate parameterized queries (no SQL injection)
- **M1.46**: RBAC enforcement at controller layer (method security)
- **M1.47**: OAuth2 scope validation against whitelist
- **M1.48**: Non-root container users (UID 1000)
- **M1.49**: Dropped capabilities (CAP_DROP ALL)
- **M1.50**: Read-only root filesystem
- **M1.51**: no-new-privileges security option
- **M1.52**: User namespace remapping (production)
- **M1.53**: Input validation on all role/permission parameters

**Residual Risk**: LOW - Multiple defense layers

---

### Component 2: Workflow Engine & LLM Service

#### S - Spoofing Identity

**Threats:**
- **T2.1**: Attacker impersonates LLM API service
- **T2.2**: Attacker spoofs workflow run owner
- **T2.3**: Agent-to-agent (A2A) communication spoofed

**Mitigations:**
- **M2.1**: LLM API key stored in Vault
- **M2.2**: HTTPS certificate validation for LLM endpoints
- **M2.3**: Run ownership validated via JWT user ID
- **M2.4**: A2A bearer token authentication
- **M2.5**: Workflow metadata signed with user context
- **M2.6**: LLM response validation (schema checks)

**Residual Risk**: MEDIUM - Compromised LLM API key could impersonate service

#### T - Tampering with Data

**Threats:**
- **T2.4**: Attacker modifies workflow run status
- **T2.5**: LLM response tampered in transit
- **T2.6**: Workflow configuration modified to execute malicious code
- **T2.7**: Agent output manipulated before storage

**Mitigations:**
- **M2.7**: RBAC enforcement on run status updates (RUN_UPDATE permission)
- **M2.8**: HTTPS for LLM API calls
- **M2.9**: Input validation on workflow configuration
- **M2.10**: Agent output sanitization before display
- **M2.11**: Database integrity constraints on run status (valid enum values)
- **M2.12**: Audit logging of all run mutations
- **M2.13**: Workflow configuration schema validation (JSON Schema)
- **M2.14**: Digital signatures on workflow results (future)

**Residual Risk**: MEDIUM - Admin users can modify runs

#### R - Repudiation

**Threats:**
- **T2.8**: User denies executing workflow run
- **T2.9**: Admin denies modifying workflow configuration
- **T2.10**: Workflow mutation (graft/prune) denied

**Mitigations:**
- **M2.15**: Audit logging of run creation with user ID
- **M2.16**: Audit logging of workflow configuration changes
- **M2.17**: Collaboration events table (all grafts, prunes, flags)
- **M2.18**: Immutable event sourcing for workflow state changes
- **M2.19**: Timestamped audit trail with user attribution
- **M2.20**: Retention policy for audit logs (90 days minimum)

**Residual Risk**: LOW - Comprehensive audit trail

#### I - Information Disclosure

**Threats:**
- **T2.11**: LLM API key exposed in logs
- **T2.12**: Sensitive workflow data visible to unauthorized users
- **T2.13**: LLM responses contain sensitive information leaked in logs
- **T2.14**: Run artifacts accessible without authorization

**Mitigations:**
- **M2.21**: LLM API key stored in Vault, never logged
- **M2.22**: API keys masked in logs (first 8 chars only)
- **M2.23**: RBAC on run access (RUN_VIEW permission)
- **M2.24**: Minimal data in logs (no PII, no secrets)
- **M2.25**: Run artifacts require RUN_VIEW permission to download
- **M2.26**: Artifact downloads rate limited (10/min per user)
- **M2.27**: LLM responses sanitized before logging
- **M2.28**: Structured logging with sensitive field filtering
- **M2.29**: Log aggregation with access controls

**Residual Risk**: MEDIUM - LLM provider could log sensitive prompts

#### D - Denial of Service

**Threats:**
- **T2.15**: Attacker creates excessive workflow runs
- **T2.16**: LLM API quota exhaustion
- **T2.17**: Large workflow configuration causes memory exhaustion
- **T2.18**: Infinite loop in workflow execution

**Mitigations:**
- **M2.30**: Rate limiting on run creation
- **M2.31**: LLM API circuit breaker (fallback to secondary)
- **M2.32**: Workflow configuration size limits (max 1MB JSON)
- **M2.33**: Workflow execution timeout (30 minutes default)
- **M2.34**: Container resource limits (CPU: 2 cores, Memory: 2GB)
- **M2.35**: Database query timeout (30 seconds)
- **M2.36**: Max concurrent runs per user (configurable)
- **M2.37**: LLM response size limits (max 100KB)
- **M2.38**: Retry limits with exponential backoff
- **M2.39**: Dead letter queue for failed runs

**Residual Risk**: MEDIUM - Well-funded attacker could exhaust LLM quota

#### E - Elevation of Privilege

**Threats:**
- **T2.19**: User executes workflow with elevated permissions
- **T2.20**: Workflow configuration exploits code injection
- **T2.21**: LLM prompt injection to execute unauthorized operations

**Mitigations:**
- **M2.40**: RBAC checks on run execution (RUN_EXECUTE permission)
- **M2.41**: Input sanitization on all workflow parameters
- **M2.42**: No dynamic code execution from user input
- **M2.43**: LLM prompt injection safeguards (input validation)
- **M2.44**: Sandboxed agent execution (containers)
- **M2.45**: Least privilege principle for workflow engine
- **M2.46**: Workflow configuration whitelist validation
- **M2.47**: LLM response parsing with strict schema validation

**Residual Risk**: MEDIUM - LLM prompt injection is an evolving threat

---

### Component 3: Real-Time Collaboration (WebSocket)

#### S - Spoofing Identity

**Threats:**
- **T3.1**: Attacker spoofs user ID in WebSocket connection
- **T3.2**: Unauthorized user joins collaboration session
- **T3.3**: User presence indicators manipulated

**Mitigations:**
- **M3.1**: JWT authentication on WebSocket handshake
- **M3.2**: User ID extracted from validated JWT token
- **M3.3**: STOMP authentication required for subscription
- **M3.4**: Run access validated (RUN_VIEW permission)
- **M3.5**: User session tracking by server
- **M3.6**: Automatic disconnect on invalid authentication

**Residual Risk**: LOW - Strong authentication controls

#### T - Tampering with Data

**Threats:**
- **T3.4**: Attacker modifies collaboration events (graft/prune/flag)
- **T3.5**: WebSocket message tampering in transit
- **T3.6**: Concurrent edit conflicts cause data corruption
- **T3.7**: Cursor position manipulation

**Mitigations:**
- **M3.7**: RBAC on collaboration mutations (RUN_UPDATE permission)
- **M3.8**: WSS (WebSocket Secure) over TLS 1.3
- **M3.9**: Operational transformation (OT) for conflict resolution
- **M3.10**: Last-Write-Wins (LWW) strategy for grafts
- **M3.11**: Set-based CRDT for prune operations
- **M3.12**: Server-side validation of all mutation messages
- **M3.13**: Message sequence numbers for ordering
- **M3.14**: Persisted collaboration messages (replay protection)
- **M3.15**: Schema validation on all WebSocket messages

**Residual Risk**: LOW - OT provides conflict resolution

#### R - Repudiation

**Threats:**
- **T3.8**: User denies performing graft/prune/flag operation
- **T3.9**: User denies participation in collaboration session

**Mitigations:**
- **M3.16**: All collaboration events logged in collaboration_events table
- **M3.17**: User ID, timestamp, event type, event data captured
- **M3.18**: Immutable append-only audit log
- **M3.19**: WebSocket connection tracking (session ID, user ID, timestamps)
- **M3.20**: Admin API for querying collaboration history
- **M3.21**: Queryable event history via REST API

**Residual Risk**: LOW - Full audit trail

#### I - Information Disclosure

**Threats:**
- **T3.10**: Unauthorized user subscribes to collaboration topic
- **T3.11**: Sensitive run data visible in WebSocket messages
- **T3.12**: User presence leaks information about who's working on what
- **T3.13**: WebSocket messages logged with sensitive data

**Mitigations:**
- **M3.22**: RBAC on topic subscription (RUN_VIEW permission)
- **M3.23**: Topic subscription requires run access validation
- **M3.24**: Minimal data in presence messages (user ID, timestamp only)
- **M3.25**: Run data not included in WebSocket messages (reference by ID)
- **M3.26**: WSS encryption prevents eavesdropping
- **M3.27**: WebSocket messages not logged in production
- **M3.28**: Admin endpoints require ADMIN role

**Residual Risk**: LOW - Proper access controls

#### D - Denial of Service

**Threats:**
- **T3.14**: WebSocket connection flooding
- **T3.15**: Excessive collaboration events (spam)
- **T3.16**: Large message payloads cause memory exhaustion
- **T3.17**: Reconnection storms

**Mitigations:**
- **M3.29**: Connection limits per user (configurable)
- **M3.30**: Message rate limiting on WebSocket events
- **M3.31**: Message size limits (max 64KB per message)
- **M3.32**: Automatic disconnect on rate limit violation
- **M3.33**: Exponential backoff on reconnection attempts
- **M3.34**: Circuit breaker on WebSocket service
- **M3.35**: Resource limits on WebSocket threads
- **M3.36**: Heartbeat timeout (disconnect idle connections)
- **M3.37**: Max connections per run (prevent monopolization)
- **M3.38**: Connection health monitoring (detect abuse)

**Residual Risk**: MEDIUM - Distributed WebSocket flood could impact service

#### E - Elevation of Privilege

**Threats:**
- **T3.18**: User performs administrative WebSocket operations
- **T3.19**: Collaboration mutation bypasses RBAC
- **T3.20**: WebSocket message injection exploits server logic

**Mitigations:**
- **M3.39**: RBAC enforcement on all WebSocket handlers
- **M3.40**: Admin endpoints require ADMIN role
- **M3.41**: Mutation messages validated against user permissions
- **M3.42**: Input sanitization on all WebSocket payloads
- **M3.43**: No code execution from WebSocket messages
- **M3.44**: Message type whitelist (only known types processed)
- **M3.45**: Least privilege for WebSocket service

**Residual Risk**: LOW - Strong authorization controls

---

### Component 4: File Upload & Artifact Storage

#### S - Spoofing Identity

**Threats:**
- **T4.1**: Attacker uploads file impersonating another user
- **T4.2**: Artifact metadata manipulated to show wrong uploader

**Mitigations:**
- **M4.1**: JWT authentication required for upload
- **M4.2**: Uploader user ID extracted from JWT token
- **M4.3**: uploaded_by field immutable in database
- **M4.4**: Artifact metadata includes authenticated user ID
- **M4.5**: Upload timestamp server-generated (not client)

**Residual Risk**: LOW - Strong authentication

#### T - Tampering with Data

**Threats:**
- **T4.3**: Attacker modifies uploaded file on filesystem
- **T4.4**: Artifact metadata tampered in database
- **T4.5**: File replaced after upload
- **T4.6**: Malicious file uploaded with fake MIME type

**Mitigations:**
- **M4.6**: POSIX file permissions 600 (owner read/write only)
- **M4.7**: Files stored with UUID names (prevent predictable paths)
- **M4.8**: Non-root container user (UID 1000)
- **M4.9**: Read-only root filesystem (tmpfs for uploads)
- **M4.10**: Content-based MIME type detection (Apache Tika)
- **M4.11**: File size validation (max 50MB)
- **M4.12**: Allowed MIME type whitelist (text, JSON, PDF, PNG, JPEG)
- **M4.13**: Filename sanitization (removes path traversal)
- **M4.14**: Virus scanning integration point (ClamAV ready)
- **M4.15**: Checksums for file integrity (future)

**Residual Risk**: MEDIUM - Admin with filesystem access could modify files

#### R - Repudiation

**Threats:**
- **T4.7**: User denies uploading file
- **T4.8**: User denies downloading artifact

**Mitigations:**
- **M4.16**: uploaded_by field tracks uploader user ID
- **M4.17**: uploaded_at timestamp for all uploads
- **M4.18**: Audit logging of upload events
- **M4.19**: Audit logging of download events (future)
- **M4.20**: IP address logging for uploads/downloads (future)

**Residual Risk**: MEDIUM - Download audit logging not yet implemented

#### I - Information Disclosure

**Threats:**
- **T4.9**: Unauthorized user downloads sensitive artifact
- **T4.10**: Artifact accessible via predictable URL
- **T4.11**: Filenames leak sensitive information
- **T4.12**: File metadata exposes PII

**Mitigations:**
- **M4.21**: RBAC on download (RUN_VIEW permission required)
- **M4.22**: UUID-based artifact IDs (not predictable)
- **M4.23**: UUID-based filenames (original name in metadata)
- **M4.24**: Rate limiting on downloads (10/min per user)
- **M4.25**: No directory listing (files accessed by ID only)
- **M4.26**: Artifact stored outside web root
- **M4.27**: Content-Disposition: attachment (forces download)
- **M4.28**: No sensitive data in filenames
- **M4.29**: TLS for all file transfers

**Residual Risk**: LOW - Strong access controls

#### D - Denial of Service

**Threats:**
- **T4.13**: Attacker uploads massive files to exhaust disk
- **T4.14**: Attacker floods download endpoint
- **T4.15**: Storage quota exhaustion

**Mitigations:**
- **M4.30**: File size limit (50MB per file)
- **M4.31**: Storage quota per run (future)
- **M4.32**: Download rate limiting (10/min per user)
- **M4.33**: Artifact retention policy (auto-cleanup after 90 days)
- **M4.34**: Disk space monitoring and alerting
- **M4.35**: Upload rate limiting (future)
- **M4.36**: Container volume size limits

**Residual Risk**: MEDIUM - No per-user storage quota yet

#### E - Elevation of Privilege

**Threats:**
- **T4.16**: Uploaded file exploits path traversal to escape container
- **T4.17**: Malicious file executed on server
- **T4.18**: Uploaded file overwrites system files

**Mitigations:**
- **M4.37**: Filename sanitization (removes ../ patterns)
- **M4.38**: Files stored in dedicated directory (/app/artifacts/{runId}/)
- **M4.39**: No code execution from uploaded files
- **M4.40**: Read-only root filesystem (prevents overwrite)
- **M4.41**: Non-root container user (no permission to modify system)
- **M4.42**: Dropped capabilities (CAP_DROP ALL)
- **M4.43**: MIME type validation (no executables allowed)
- **M4.44**: Virus scanning (ClamAV integration point)
- **M4.45**: Tmpfs mount with noexec flag

**Residual Risk**: LOW - Multiple filesystem protections

---

### Component 5: Database (PostgreSQL)

#### S - Spoofing Identity

**Threats:**
- **T5.1**: Attacker impersonates database user
- **T5.2**: Application impersonates another application

**Mitigations:**
- **M5.1**: Strong database password stored in Vault
- **M5.2**: Database password rotation (quarterly)
- **M5.3**: TLS 1.3 client certificate authentication (optional)
- **M5.4**: Connection string with SSL mode: require
- **M5.5**: Database user limited to specific host/IP
- **M5.6**: Separate database users per service (future)

**Residual Risk**: LOW - Strong authentication

#### T - Tampering with Data

**Threats:**
- **T5.3**: SQL injection modifies database records
- **T5.4**: Direct database access bypasses application controls
- **T5.5**: Backup restore overwrites recent data
- **T5.6**: Database integrity constraints violated

**Mitigations:**
- **M5.7**: JPA/Hibernate parameterized queries (no SQL injection)
- **M5.8**: Database access limited to application only (network isolation)
- **M5.9**: Database integrity constraints (foreign keys, NOT NULL, CHECK)
- **M5.10**: Backup retention and versioning
- **M5.11**: Point-in-time recovery capability
- **M5.12**: Database audit logging (pg_audit) (future)
- **M5.13**: Database read replicas for reporting (prevents write access)
- **M5.14**: Application-level validation before database write

**Residual Risk**: MEDIUM - Database administrator has full access

#### R - Repudiation

**Threats:**
- **T5.7**: Database admin denies modifying data
- **T5.8**: Application user denies database operation

**Mitigations:**
- **M5.15**: Application audit logging (all CRUD operations)
- **M5.16**: Database audit logging with pg_audit (future)
- **M5.17**: Immutable audit tables (append-only)
- **M5.18**: Timestamp tracking (created_at, updated_at)
- **M5.19**: User ID tracking on all tables
- **M5.20**: Database transaction logs retained

**Residual Risk**: MEDIUM - pg_audit not yet enabled

#### I - Information Disclosure

**Threats:**
- **T5.9**: Database dump exposes sensitive data
- **T5.10**: Backup files contain unencrypted secrets
- **T5.11**: Database logs expose passwords or tokens
- **T5.12**: Network traffic exposes database queries

**Mitigations:**
- **M5.21**: Column-level encryption (AES-256-GCM) for sensitive fields
- **M5.22**: Encrypted fields: OAuth tokens, MFA secrets
- **M5.23**: Password hashing (BCrypt, never stored plain)
- **M5.24**: Refresh tokens hashed (SHA-256) before storage
- **M5.25**: TLS 1.3 for database connections
- **M5.26**: Encrypted backups (future)
- **M5.27**: No secrets in database logs
- **M5.28**: Database stored on encrypted volume (LUKS/cloud encryption)
- **M5.29**: Access logs monitor for unauthorized queries
- **M5.30**: Encryption keys stored in Vault (not database)

**Residual Risk**: MEDIUM - Backup encryption not yet implemented

#### D - Denial of Service

**Threats:**
- **T5.13**: Attacker exhausts database connections
- **T5.14**: Slow queries cause database overload
- **T5.15**: Disk space exhaustion from logs/data

**Mitigations:**
- **M5.31**: Connection pooling with max pool size (HikariCP)
- **M5.32**: Database query timeout (30 seconds)
- **M5.33**: Container resource limits (CPU: 2 cores, Memory: 2GB)
- **M5.34**: Database performance monitoring (slow query log)
- **M5.35**: Disk space monitoring and alerting
- **M5.36**: Log rotation and retention policy
- **M5.37**: Auto-vacuum configuration
- **M5.38**: Database indexes on frequent queries

**Residual Risk**: MEDIUM - Poorly optimized queries could still impact performance

#### E - Elevation of Privilege

**Threats:**
- **T5.16**: SQL injection grants admin access
- **T5.17**: Database user escalates to superuser
- **T5.18**: Container breakout accesses database files

**Mitigations:**
- **M5.39**: JPA/Hibernate parameterized queries (no SQL injection)
- **M5.40**: Database user has minimal privileges (no SUPERUSER)
- **M5.41**: Database user cannot create/drop databases
- **M5.42**: Database files owned by postgres user (UID 999)
- **M5.43**: Non-root container user
- **M5.44**: Read-only root filesystem
- **M5.45**: User namespace remapping (production)
- **M5.46**: No dynamic SQL from user input
- **M5.47**: Input validation on all query parameters

**Residual Risk**: LOW - Strong controls

---

### Component 6: HashiCorp Vault

#### S - Spoofing Identity

**Threats:**
- **T6.1**: Attacker impersonates application to access Vault
- **T6.2**: Attacker steals Vault token/AppRole credentials

**Mitigations:**
- **M6.1**: AppRole authentication in production (not root token)
- **M6.2**: Role ID and Secret ID separate channels
- **M6.3**: Token TTL limits (1 hour)
- **M6.4**: TLS 1.3 for Vault communication
- **M6.5**: Vault token stored in memory only (not filesystem)
- **M6.6**: Kubernetes authentication in K8s deployments

**Residual Risk**: MEDIUM - Stolen AppRole credentials grant Vault access

#### T - Tampering with Data

**Threats:**
- **T6.3**: Attacker modifies secrets in Vault
- **T6.4**: Network MITM attack modifies Vault responses
- **T6.5**: Unauthorized secret version rollback

**Mitigations:**
- **M6.7**: Vault ACL policies (read-only for application)
- **M6.8**: Write access limited to admin role only
- **M6.9**: TLS 1.3 prevents MITM
- **M6.10**: Vault audit logging of all secret access
- **M6.11**: KV v2 engine versioning (immutable history)
- **M6.12**: Secret checksum validation

**Residual Risk**: LOW - Strong access controls

#### R - Repudiation

**Threats:**
- **T6.6**: Admin denies modifying Vault secret
- **T6.7**: Application denies reading sensitive secret

**Mitigations:**
- **M6.13**: Vault audit logging enabled (all operations)
- **M6.14**: Audit logs sent to secure log aggregation
- **M6.15**: Audit log retention (90 days minimum)
- **M6.16**: Immutable audit log (append-only file)
- **M6.17**: Log review in security audits

**Residual Risk**: LOW - Comprehensive audit trail

#### I - Information Disclosure

**Threats:**
- **T6.8**: Vault secrets exposed in logs
- **T6.9**: Vault unsealed keys compromised
- **T6.10**: Backup contains unencrypted Vault data
- **T6.11**: Network sniffing captures secrets in transit

**Mitigations:**
- **M6.18**: Secrets never logged by application
- **M6.19**: Vault audit logs do not contain secret values
- **M6.20**: Unseal keys distributed (3 of 5 Shamir shares)
- **M6.21**: Unseal keys stored in separate secure locations
- **M6.22**: Vault snapshots encrypted
- **M6.23**: TLS 1.3 for all Vault communication
- **M6.24**: Vault network isolated (private network)
- **M6.25**: Secrets masked in application logs

**Residual Risk**: MEDIUM - Unseal key compromise would expose all secrets

#### D - Denial of Service

**Threats:**
- **T6.12**: Vault service unavailable (sealed, crashed)
- **T6.13**: Vault request flooding
- **T6.14**: Vault storage exhausted

**Mitigations:**
- **M6.26**: Vault health checks and alerting
- **M6.27**: Automatic application fallback to environment variables
- **M6.28**: Vault HA cluster (production)
- **M6.29**: Vault rate limiting
- **M6.30**: Storage monitoring and cleanup
- **M6.31**: Container resource limits on Vault
- **M6.32**: Vault backup and restore procedures

**Residual Risk**: MEDIUM - Single Vault instance in dev/staging

#### E - Elevation of Privilege

**Threats:**
- **T6.15**: Application reads secrets outside allowed path
- **T6.16**: Privilege escalation to admin role
- **T6.17**: Container breakout accesses Vault files

**Mitigations:**
- **M6.33**: Vault ACL policies enforce path restrictions
- **M6.34**: Application role limited to secret/data/atlasia/* path
- **M6.35**: No policy escalation from application token
- **M6.36**: Vault files in encrypted volume
- **M6.37**: Non-root container user for Vault
- **M6.38**: Dropped capabilities
- **M6.39**: Policy review in security audits

**Residual Risk**: LOW - Strong policy controls

---

### Component 7: Container Runtime

#### S - Spoofing Identity

**Threats:**
- **T7.1**: Container impersonates another service
- **T7.2**: Network traffic spoofing between containers

**Mitigations:**
- **M7.1**: Service authentication via bearer tokens
- **M7.2**: Container-to-container TLS (future)
- **M7.3**: Network policies (Kubernetes)
- **M7.4**: Service mesh (future)

**Residual Risk**: MEDIUM - No mutual TLS between services yet

#### T - Tampering with Data

**Threats:**
- **T7.3**: Attacker modifies container image
- **T7.4**: Container filesystem modified at runtime
- **T7.5**: Container configuration tampered

**Mitigations:**
- **M7.5**: Container image signing (future)
- **M7.6**: Image hash verification
- **M7.7**: Read-only root filesystem
- **M7.8**: Tmpfs mounts with noexec flag
- **M7.9**: Trivy scanning for vulnerabilities
- **M7.10**: Immutable infrastructure (containers recreated, not modified)

**Residual Risk**: LOW - Read-only filesystem prevents tampering

#### R - Repudiation

**Threats:**
- **T7.6**: Container operations not logged
- **T7.7**: User denies deploying malicious container

**Mitigations:**
- **M7.11**: Container audit logging (Docker events)
- **M7.12**: CI/CD audit trail (GitHub Actions logs)
- **M7.13**: Deployment logs with user attribution
- **M7.14**: Container orchestration logs (Kubernetes audit)

**Residual Risk**: LOW - Audit trail in place

#### I - Information Disclosure

**Threats:**
- **T7.8**: Secrets embedded in container image
- **T7.9**: Sensitive data in container logs
- **T7.10**: Container metadata exposes sensitive info

**Mitigations:**
- **M7.15**: Secrets scanning in CI/CD (Trivy)
- **M7.16**: No secrets in Dockerfiles or images
- **M7.17**: Secrets injected at runtime (Vault)
- **M7.18**: Log filtering (no secrets, minimal PII)
- **M7.19**: Container labels do not contain secrets
- **M7.20**: Environment variables from Vault

**Residual Risk**: LOW - Secrets management enforced

#### D - Denial of Service

**Threats:**
- **T7.11**: Container resource exhaustion (CPU/memory)
- **T7.12**: Container crashes impact other containers
- **T7.13**: Disk space exhaustion from logs

**Mitigations:**
- **M7.21**: Container resource limits (CPU: 2 cores, Memory: 2GB)
- **M7.22**: Container resource reservations
- **M7.23**: Log rotation with size limits
- **M7.24**: Health checks and auto-restart
- **M7.25**: Circuit breakers between services
- **M7.26**: Container isolation (cgroups)

**Residual Risk**: LOW - Resource controls in place

#### E - Elevation of Privilege

**Threats:**
- **T7.14**: Container breakout to host
- **T7.15**: Container runs as root
- **T7.16**: Privilege escalation via capabilities

**Mitigations:**
- **M7.27**: Non-root users (UID 1000, 101, 999)
- **M7.28**: Dropped capabilities (CAP_DROP ALL)
- **M7.29**: Read-only root filesystem
- **M7.30**: no-new-privileges security option
- **M7.31**: User namespace remapping (production)
- **M7.32**: Seccomp profile (future)
- **M7.33**: AppArmor/SELinux (future)
- **M7.34**: Minimal base images (Alpine)
- **M7.35**: Package managers removed from images

**Residual Risk**: LOW - Multiple container hardening layers

---

## Attack Scenarios

### Scenario 1: Compromised OAuth2 Access Token

**Attack Vector:**
1. Attacker obtains GitHub OAuth2 access token via phishing
2. Attacker uses token to authenticate to Atlasia
3. Attacker accesses all runs visible to compromised account

**Mitigations:**
- **M1.4**: OAuth2 tokens encrypted at rest (AES-256-GCM)
- **M1.5**: HTTPS/TLS 1.3 prevents token interception
- **M1.18**: Audit logging of all OAuth2 logins
- **M3.16**: Collaboration events logged (detects suspicious activity)
- **M5.17**: Immutable audit trail for forensics

**Detection:**
- Unusual login location/IP address
- Login from multiple locations simultaneously
- Access to runs user doesn't normally view
- Elevated API request rate

**Response:**
1. Revoke OAuth2 token in database (set revoked=true)
2. Force logout all sessions for user
3. Notify user via email
4. Review audit logs for unauthorized actions
5. Restore any tampered data from backup

**Residual Risk:** MEDIUM - Attacker has access until token revoked

---

### Scenario 2: SQL Injection Attack

**Attack Vector:**
1. Attacker crafts malicious input to workflow parameter
2. Attacker attempts SQL injection in run name/description
3. Attacker tries to read/modify database directly

**Mitigations:**
- **M2.9**: Input validation on workflow configuration
- **M2.13**: JSON Schema validation
- **M4.13**: Filename sanitization
- **M5.7**: JPA/Hibernate parameterized queries
- **M5.46**: No dynamic SQL from user input
- **M5.47**: Input validation on all query parameters

**Detection:**
- Web Application Firewall (WAF) detects SQL patterns
- Application logs show validation errors
- Database logs show invalid query attempts

**Response:**
1. Input validation blocks malicious request (HTTP 400)
2. Alert security team on repeated attempts
3. Rate limit or block offending IP address
4. Review logs for successful injection attempts

**Residual Risk:** LOW - JPA parameterized queries prevent SQL injection

---

### Scenario 3: Cross-Site Scripting (XSS)

**Attack Vector:**
1. Attacker injects malicious JavaScript in workflow name
2. Script stored in database and rendered to other users
3. Script executes in victim browser, steals JWT token

**Mitigations:**
- **M2.10**: Agent output sanitization before display
- **M4.13**: Filename sanitization
- **M3.42**: Input sanitization on WebSocket payloads
- **M5.14**: Application-level validation before database write
- Frontend: Angular's built-in XSS protection (DOM sanitization)

**Detection:**
- WAF detects <script> tags in requests
- Content Security Policy (CSP) violations logged
- User reports suspicious behavior

**Response:**
1. Sanitize stored XSS payload in database
2. Deploy CSP headers (future)
3. Review all user-generated content for XSS
4. Invalidate stolen JWT tokens

**Residual Risk:** MEDIUM - Stored XSS if sanitization bypassed

---

### Scenario 4: Cross-Site Request Forgery (CSRF)

**Attack Vector:**
1. Attacker creates malicious website
2. Victim visits site while authenticated to Atlasia
3. Site submits forged request to Atlasia API
4. Attacker creates workflow run or modifies data

**Mitigations:**
- **M1.12**: OAuth2 state parameter validation
- **M3.3**: STOMP authentication required
- Backend: CSRF protection enabled (Spring Security)
- Frontend: Custom X-XSRF-TOKEN header

**Detection:**
- Missing or invalid CSRF token
- Request origin does not match expected domain
- Audit logs show unusual actions

**Response:**
1. Reject requests with invalid CSRF token (HTTP 403)
2. Alert on repeated CSRF attempts from same IP
3. Review audit logs for successful attacks

**Residual Risk:** LOW - CSRF protection enabled

---

### Scenario 5: Container Escape

**Attack Vector:**
1. Attacker exploits container vulnerability (kernel exploit)
2. Attacker escapes container to host system
3. Attacker gains root access on host
4. Attacker accesses all data, secrets, and other containers

**Mitigations:**
- **M7.27**: Non-root users (prevents many exploits)
- **M7.28**: Dropped capabilities (limits kernel access)
- **M7.29**: Read-only root filesystem
- **M7.30**: no-new-privileges
- **M7.31**: User namespace remapping (container root != host root)
- **M7.9**: Trivy scanning (detects known vulnerabilities)
- **M7.34**: Minimal base images (smaller attack surface)

**Detection:**
- Host intrusion detection system (HIDS)
- Unusual process activity on host
- Container resource usage anomaly
- Audit logs show privilege escalation attempts

**Response:**
1. Immediately isolate affected host from network
2. Capture forensic evidence (memory dump, disk image)
3. Terminate all containers on host
4. Rebuild host from clean image
5. Review container images for vulnerabilities
6. Rotate all secrets (assume compromise)

**Residual Risk:** LOW - Multiple containment layers make escape difficult

---

### Scenario 6: Secret Leakage in Logs

**Attack Vector:**
1. Developer accidentally logs JWT token or API key
2. Logs aggregated to central logging system
3. Attacker gains access to logging system
4. Attacker extracts secrets from logs

**Mitigations:**
- **M1.31**: Token values masked in logs
- **M2.21**: LLM API key never logged
- **M2.22**: API keys masked in logs (first 8 chars only)
- **M2.27**: LLM responses sanitized before logging
- **M6.18**: Vault secrets never logged
- **M7.18**: Log filtering (no secrets, minimal PII)

**Detection:**
- Automated secrets scanning in logs (Trivy)
- Manual log review
- Unusual API activity from leaked credentials

**Response:**
1. Immediately rotate leaked secret
2. Invalidate all tokens using that secret
3. Review logs for unauthorized access using leaked secret
4. Improve log filtering to prevent recurrence
5. Educate developers on secure logging

**Residual Risk:** MEDIUM - Human error can bypass technical controls

---

### Scenario 7: Denial of Service (DDoS)

**Attack Vector:**
1. Attacker launches distributed attack from botnet
2. Floods API endpoints with requests
3. Exhausts server resources (CPU, memory, connections)
4. Service becomes unavailable to legitimate users

**Mitigations:**
- **M1.36**: Rate limiting on login endpoint
- **M1.41**: Circuit breakers for external services
- **M1.42**: Container resource limits
- **M2.30**: Rate limiting on run creation
- **M2.34**: Container resource limits
- **M3.29**: WebSocket connection limits
- **M3.30**: WebSocket message rate limiting
- **M4.32**: Download rate limiting

**Detection:**
- Sudden spike in request rate
- Elevated CPU/memory usage
- Increased error rate (429, 503)
- Service health checks failing

**Response:**
1. Enable aggressive rate limiting
2. Block attacking IP ranges (firewall)
3. Enable CDN/DDoS protection (Cloudflare)
4. Scale up infrastructure (horizontal scaling)
5. Activate incident response team

**Residual Risk:** HIGH - Large-scale DDoS difficult to mitigate without specialized services

---

### Scenario 8: LLM Prompt Injection

**Attack Vector:**
1. Attacker crafts malicious workflow prompt
2. Prompt injection bypasses LLM safeguards
3. LLM executes unauthorized operations or leaks data
4. Attacker extracts sensitive information from LLM context

**Mitigations:**
- **M2.9**: Input validation on workflow configuration
- **M2.43**: LLM prompt injection safeguards
- **M2.47**: LLM response parsing with strict schema validation
- **M2.23**: RBAC on run access (limits data exposure)
- **M2.27**: LLM responses sanitized before logging

**Detection:**
- Unusual LLM response patterns
- LLM API errors or warnings
- Schema validation failures
- User reports unexpected behavior

**Response:**
1. Block malicious prompt patterns
2. Improve prompt injection detection
3. Review LLM responses for leaked data
4. Adjust LLM system prompt for better safety
5. Consider LLM provider's safety features

**Residual Risk:** HIGH - Prompt injection is an evolving threat with no perfect solution

---

## Security Controls Matrix

| Component | Spoofing | Tampering | Repudiation | Info Disclosure | DoS | Elevation of Privilege |
|-----------|----------|-----------|-------------|-----------------|-----|------------------------|
| **Authentication** | M1.1-M1.10 | M1.11-M1.17 | M1.18-M1.24 | M1.25-M1.35 | M1.36-M1.43 | M1.44-M1.53 |
| **Workflow Engine** | M2.1-M2.6 | M2.7-M2.14 | M2.15-M2.20 | M2.21-M2.29 | M2.30-M2.39 | M2.40-M2.47 |
| **Collaboration** | M3.1-M3.6 | M3.7-M3.15 | M3.16-M3.21 | M3.22-M3.28 | M3.29-M3.38 | M3.39-M3.45 |
| **File Upload** | M4.1-M4.5 | M4.6-M4.15 | M4.16-M4.20 | M4.21-M4.29 | M4.30-M4.36 | M4.37-M4.45 |
| **Database** | M5.1-M5.6 | M5.7-M5.14 | M5.15-M5.20 | M5.21-M5.30 | M5.31-M5.38 | M5.39-M5.47 |
| **Vault** | M6.1-M6.6 | M6.7-M6.12 | M6.13-M6.17 | M6.18-M6.25 | M6.26-M6.32 | M6.33-M6.39 |
| **Containers** | M7.1-M7.4 | M7.5-M7.10 | M7.11-M7.14 | M7.15-M7.20 | M7.21-M7.26 | M7.27-M7.35 |

**Total Mitigations:** 229

---

## Threat Prioritization

### Critical (Immediate Action Required)

1. **T1.1**: JWT token theft → M1.1 (Short TTL), M1.9 (httpOnly cookies)
2. **T2.19**: Workflow privilege escalation → M2.40 (RBAC), M2.41 (Input sanitization)
3. **T5.3**: SQL injection → M5.7 (Parameterized queries)
4. **T7.14**: Container breakout → M7.27-M7.31 (Container hardening)

### High (Address Within Sprint)

5. **T1.3**: OAuth2 token compromise → M1.4 (Encryption at rest), M1.18 (Audit logging)
6. **T2.11**: LLM API key exposure → M2.21 (Vault storage), M2.22 (Log masking)
7. **T3.14**: WebSocket flooding → M3.29-M3.32 (Connection/rate limits)
8. **T4.9**: Unauthorized artifact download → M4.21 (RBAC), M4.24 (Rate limiting)
9. **T6.8**: Vault secrets in logs → M6.18 (Never log secrets), M6.25 (Masking)

### Medium (Address Within Quarter)

10. **T1.13**: JWT exposes PII → M1.25-M1.26 (Minimal claims)
11. **T2.15**: Excessive workflow runs → M2.30 (Rate limiting), M2.36 (Max concurrent)
12. **T3.10**: Unauthorized WebSocket subscription → M3.22-M3.23 (RBAC)
13. **T4.13**: Disk exhaustion → M4.30 (File size limits), M4.33 (Retention policy)
14. **T5.9**: Database dump exposure → M5.21-M5.24 (Column encryption), M5.26 (Encrypted backups)

### Low (Monitor and Review)

15. **T1.19**: Login flooding → M1.36 (Rate limiting), M1.37 (Brute force protection)
16. **T2.8**: Workflow repudiation → M2.15-M2.17 (Audit logging)
17. **T3.12**: Presence info disclosure → M3.24 (Minimal data)
18. **T4.7**: Upload repudiation → M4.16-M4.18 (Audit logging)
19. **T7.6**: Container operation repudiation → M7.11-M7.14 (Audit logging)

---

## Residual Risks

### High Residual Risk

1. **Distributed Denial of Service (DDoS)**
   - **Threat**: T3.14, T1.19, T2.15
   - **Current Mitigations**: Rate limiting, resource limits, circuit breakers
   - **Residual Risk**: Large-scale DDoS can overwhelm infrastructure
   - **Recommendation**: Implement CDN/DDoS protection (Cloudflare, AWS Shield)
   - **Acceptance Criteria**: CTO approval required

2. **LLM Prompt Injection**
   - **Threat**: T2.21
   - **Current Mitigations**: Input validation, response parsing
   - **Residual Risk**: Evolving attack vectors, no perfect solution
   - **Recommendation**: Continuous monitoring, LLM provider safety features, prompt engineering
   - **Acceptance Criteria**: Accept as inherent risk of LLM usage

### Medium Residual Risk

3. **Malware on User Device**
   - **Threat**: T1.1 (JWT theft from localStorage/memory)
   - **Current Mitigations**: Short TTL, token rotation, HTTPS
   - **Residual Risk**: Malware with admin privileges can extract tokens
   - **Recommendation**: Encourage users to use httpOnly cookies, implement CSP
   - **Acceptance Criteria**: User education, endpoint security responsibility

4. **Database Administrator Privilege Abuse**
   - **Threat**: T5.4 (Direct database access), T5.14 (Slow queries)
   - **Current Mitigations**: Audit logging, column encryption
   - **Residual Risk**: DBA can decrypt data with encryption key
   - **Recommendation**: Implement separation of duties, Vault key access controls
   - **Acceptance Criteria**: Documented DBA procedures, quarterly audit

5. **Vault Unseal Key Compromise**
   - **Threat**: T6.10
   - **Current Mitigations**: Shamir secret sharing (3 of 5 keys), encrypted snapshots
   - **Residual Risk**: If threshold keys compromised, all secrets exposed
   - **Recommendation**: Auto-unseal with cloud KMS (AWS/GCP), key ceremony procedures
   - **Acceptance Criteria**: Production use of auto-unseal

6. **Backup Encryption Not Implemented**
   - **Threat**: T5.10 (Unencrypted backups)
   - **Current Mitigations**: Column encryption for sensitive fields
   - **Residual Risk**: Backup files contain non-encrypted data
   - **Recommendation**: Implement encrypted backups (GPG, LUKS)
   - **Acceptance Criteria**: All production backups encrypted

### Low Residual Risk

7. **Service-to-Service Mutual TLS Not Implemented**
   - **Threat**: T7.1 (Container impersonation)
   - **Current Mitigations**: Bearer token authentication
   - **Residual Risk**: Network-level attacks between services
   - **Recommendation**: Implement service mesh (Istio) or container-to-container TLS
   - **Acceptance Criteria**: Production deployment with mTLS

8. **Download Audit Logging Not Implemented**
   - **Threat**: T4.8 (Download repudiation)
   - **Current Mitigations**: Upload logging, rate limiting
   - **Residual Risk**: Cannot prove user downloaded artifact
   - **Recommendation**: Implement download audit logging
   - **Acceptance Criteria**: Audit log entry for each download

---

## Review and Maintenance

### Review Schedule

- **Quarterly**: Full threat model review, update residual risks
- **After Major Release**: Review new features for threats
- **After Security Incident**: Update threat model with lessons learned
- **Annual**: External security audit, penetration testing

### Document Ownership

- **Owner**: Security Team
- **Reviewers**: DevOps, Engineering, Product
- **Approvers**: CTO, CISO

### Change History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-21 | Security Team | Initial STRIDE threat model |

---

## References

- [Microsoft STRIDE Threat Modeling](https://learn.microsoft.com/en-us/azure/security/develop/threat-modeling-tool-threats)
- [OWASP Threat Modeling](https://owasp.org/www-community/Threat_Modeling)
- [NIST SP 800-154: Guide to Data-Centric System Threat Modeling](https://csrc.nist.gov/publications/detail/sp/800-154/draft)
- [CONTAINER_SECURITY.md](CONTAINER_SECURITY.md)
- [VAULT_SETUP.md](VAULT_SETUP.md)
- [JWT_AUTHENTICATION.md](JWT_AUTHENTICATION.md)
- [COLLABORATION.md](COLLABORATION.md)
- [FILE_UPLOAD_SECURITY.md](FILE_UPLOAD_SECURITY.md)
