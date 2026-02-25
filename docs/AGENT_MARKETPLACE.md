# Agent Marketplace

## Overview

The Agent Marketplace provides a comprehensive UI for browsing, filtering, and managing AI agents within the Atlasia orchestrator. It enables discovery of agents by capabilities, installation of third-party agents, version management, and real-time health status monitoring.

## Features

### 1. Agent Card Browsing
- **Visual Cards**: Each agent is displayed as a card with key information
- **Agent Details**: Name, version, vendor, role, description
- **Capabilities**: Visual list of all capabilities provided by the agent
- **Constraints**: Token limits, timeout settings, cost budgets
- **Health Status**: Real-time status badges (active/degraded/offline)

### 2. Capability Filtering
- **Multi-Select Sidebar**: Filter agents by one or more capabilities
- **Search Bar**: Quickly find specific capabilities
- **Agent Count**: Shows how many agents provide each capability
- **Highlight Matches**: Selected capabilities are highlighted in agent cards

### 3. Installation Workflow
- **One-Click Install**: Install agents with a single button click
- **Conflict Detection**: Prevents duplicate installations
- **Authorization**: Requires `AGENTS_MANAGE` permission
- **Toast Notifications**: Success/failure feedback

### 4. Version Management
- **Version Table**: Shows installed vs. available versions
- **Update Detection**: Identifies when updates are available
- **Vendor Information**: Tracks agent vendor/publisher
- **Status Monitoring**: Current operational status of each agent

### 5. Health Status Monitoring
- **Real-Time Badges**: Active, degraded, or offline status
- **Visual Indicators**: Color-coded dots and animations
- **Status Filtering**: View agents by installation status

### 6. Admin Approval Workflow
- **Permission Gating**: Installation requires `AGENTS_MANAGE` permission
- **Role-Based Access**: ADMIN and WORKFLOW_MANAGER roles have access
- **Authorization Check**: Backend validates permissions on install

## API Endpoints

### Get All Agents
```http
GET /api/a2a/agents
Authorization: Bearer {token}
```
Returns list of all registered agents with their cards.

### Get Agent by Name
```http
GET /api/a2a/agents/{name}
Authorization: Bearer {token}
```
Returns specific agent card.

### List All Capabilities
```http
GET /api/a2a/capabilities
Authorization: Bearer {token}
```
Returns set of all unique capabilities across agents.

### Install Agent
```http
POST /api/a2a/agents/install
Authorization: Bearer {token}
Content-Type: application/json

{
  "name": "custom-agent",
  "version": "1.0",
  "role": "CUSTOM",
  "vendor": "third-party",
  "description": "Custom analysis agent",
  "capabilities": ["custom_analysis", "data_processing"],
  "outputArtifactKey": "analysis_result",
  "mcpServers": [],
  "constraints": {
    "maxTokens": 4096,
    "maxDurationMs": 60000,
    "costBudgetUsd": 0.05
  },
  "transport": "http",
  "healthEndpoint": "/health",
  "status": "active"
}
```

**Responses:**
- `201 Created`: Agent installed successfully
- `409 Conflict`: Agent already exists
- `401 Unauthorized`: Missing or invalid token
- `403 Forbidden`: Insufficient permissions

## Permissions

### Required Permission
- **Resource**: `agents`
- **Action**: `manage`
- **Full Permission**: `agents:manage`

### Roles with Access
- **ADMIN**: Full access to all marketplace features
- **WORKFLOW_MANAGER**: Can install and manage agents
- **USER**: Read-only access (cannot install)
- **VIEWER**: Read-only access (cannot install)

## Frontend Component

### Location
`frontend/src/app/components/agent-marketplace.component.ts`

### Route
`/marketplace` (requires authentication)

### Key Features
- **Angular Standalone Component**: No module dependencies
- **Reactive Signals**: Uses Angular signals for state management
- **Real-Time Filtering**: Instant capability filtering with multi-select
- **Responsive Design**: Mobile-friendly grid layout
- **Toast Integration**: User feedback via ToastService

### State Management
```typescript
agents = signal<AgentCard[]>([]);
allCapabilities = signal<string[]>([]);
selectedCapabilities = signal<Set<string>>(new Set());
statusFilter = signal<'all' | 'installed' | 'available'>('all');
loading = signal(true);
installing = signal(false);
```

### Computed Properties
- `filteredCapabilities`: Filtered by search term
- `installedAgents`: Agents with local/atlasia vendor
- `installedCount`: Count of installed agents
- `availableCount`: Count of available agents
- `filteredAgents`: Agents filtered by status and capabilities

## Navigation

The marketplace is accessible from the main navigation sidebar:

```
Insights
├── Analytics
├── Failure Patterns
├── A2A Registry
└── Agent Marketplace  ← New
```

## Usage Examples

### Browse All Agents
1. Navigate to `/marketplace`
2. View all registered agents in grid layout
3. Scroll through available agents

### Filter by Capability
1. Click capability checkboxes in sidebar
2. Agents automatically filter to show only matches
3. Selected capabilities are highlighted in cards

### Install an Agent
1. Find agent in marketplace
2. Click "Install Agent" button
3. Wait for success notification
4. Agent appears in Version Management table

### Search Capabilities
1. Type in capability search box
2. Capability list filters in real-time
3. Select desired capabilities

### View Installed Agents
1. Click "Installed" tab above agent grid
2. View only installed agents
3. Check version management table below

## Technical Architecture

### Backend
- **Service**: `A2ADiscoveryService` - Core agent registry
- **Controller**: `A2AController` - REST API endpoints
- **Authorization**: `RequiresPermission` annotation with AOP
- **Permission Setup**: `RoleService` initializes permissions

### Frontend
- **Component**: `AgentMarketplaceComponent` - Main UI
- **Service**: `OrchestratorService` - HTTP client methods
- **Models**: `AgentCard`, `AgentConstraints` - TypeScript interfaces
- **Guards**: `authGuard` - Route protection

### Security
- **JWT Authentication**: Bearer token required
- **Permission Evaluation**: AOP aspect checks permissions
- **Role-Based Access**: ADMIN and WORKFLOW_MANAGER roles
- **Token Validation**: GitHub token or admin token accepted

## Testing

### Backend Tests
- `A2AControllerTest`: Tests install endpoint authorization
- `A2ADiscoveryServiceTest`: Tests capability indexing

### Test Coverage
```bash
cd ai-orchestrator
mvn test -Dtest=A2AControllerTest
mvn test -Dtest=A2ADiscoveryServiceTest
```

### Manual Testing
1. Start backend: `cd ai-orchestrator && mvn spring-boot:run`
2. Start frontend: `cd frontend && npm run start`
3. Navigate to `http://localhost:4200/marketplace`
4. Test filtering, installation, version management

## Future Enhancements

### Planned Features
- **Agent Ratings**: User reviews and ratings
- **Usage Statistics**: Agent execution metrics
- **Update Notifications**: Alert when new versions available
- **Bulk Installation**: Install multiple agents at once
- **Agent Categories**: Organize by category/domain
- **Approval Queue**: Admin approval workflow for third-party agents
- **Agent Marketplace API**: External marketplace integration
- **Agent Templates**: Quick-start templates for common use cases

### Approval Workflow (Future)
Currently, agents are installed immediately. Future implementation will include:
1. User submits install request
2. Request queued in approval table
3. Admin reviews and approves/rejects
4. Approved agents are registered
5. User receives notification

## Troubleshooting

### Issue: "Unauthorized" error
**Solution**: Ensure you're logged in and have `AGENTS_MANAGE` permission.

### Issue: Agent already installed
**Solution**: The agent name must be unique. Uninstall existing agent first.

### Issue: Capabilities not loading
**Solution**: Check backend logs for errors. Ensure A2A endpoints are accessible.

### Issue: Install button disabled
**Solution**: Check if you have the required permissions. Contact admin if needed.

## Related Documentation
- [A2A Protocol](./A2A_PROTOCOL.md)
- [Authentication](./AUTHENTICATION.md)
- [Permissions & Roles](./RBAC.md)
- [Development Guide](../AGENTS.md)
