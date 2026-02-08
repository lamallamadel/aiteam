# Atlasia Frontend Dashboard

Angular-based frontend for the Atlasia AI Orchestrator with comprehensive run analytics and monitoring.

## Structure

```
src/app/
├── components/           # UI Components
│   ├── analytics-dashboard.component.ts  # Analytics dashboard with charts
│   ├── dashboard-home.component.ts       # Home dashboard landing page
│   ├── run-list.component.ts             # Paginated run history with filters
│   ├── run-detail.component.ts           # Individual run details view
│   ├── chat-interface.ts                 # Chat interface component
│   └── index.ts                          # Component exports
├── services/             # Services
│   └── analytics.service.ts              # Analytics & Run API service
├── models/               # Data models
│   └── index.ts                          # Model interfaces
├── app.ts                # Root component
├── app.routes.ts         # Application routing
└── app.config.ts         # Application configuration
```

## Features

### Dashboard Home (`/dashboard`)
- Quick stats overview (total runs, success rate, active runs, escalations)
- Recent activity feed
- Quick action cards for navigation

### Run List (`/runs`)
- Paginated run history table
- Status filters (All, Done, Failed, Escalated, In Progress)
- Search by repository
- Status badges with color coding
- Click to view run details

### Run Detail (`/runs/:id`)
- Complete run information
- Artifact listing
- Status tracking

### Analytics Dashboard (`/analytics`)
- Success rate trend chart (line chart)
- Agent performance metrics (bar chart)
- Status distribution (doughnut chart)
- Persona effectiveness breakdown
- Configuration recommendations

## Components

### RunListComponent
Displays paginated run history with:
- Status filtering
- Repository search
- Fix count indicators
- Time-based formatting
- Clickable rows for details

### AnalyticsDashboardComponent
Comprehensive analytics using ng2-charts:
- Real-time metrics cards
- Interactive Chart.js visualizations
- Agent performance tracking
- Persona effectiveness scoring

### DashboardHomeComponent
Landing page with:
- Quick stats overview
- Recent activity
- Navigation shortcuts

### RunDetailComponent
Detailed view showing:
- Run metadata
- Artifact history
- Status information

## Services

### AnalyticsService
HTTP service for backend API:
- `getSummary()` - Get analytics summary
- `getAgentPerformance()` - Get agent performance metrics
- `getEscalationInsights()` - Get escalation analysis
- `getPersonaEffectiveness()` - Get persona effectiveness data
- `getRun(id)` - Get individual run details
- `getRunArtifacts(id)` - Get run artifacts

## Models

All TypeScript interfaces in `models/index.ts`:
- `Run` - Run entity
- `AnalyticsSummary` - Analytics summary data
- `AgentPerformance` - Agent performance metrics
- `PersonaEffectiveness` - Persona learning data
- `EscalationInsight` - Escalation analysis

## Dependencies

- **Angular 21+** - Framework
- **Chart.js** - Charting library
- **ng2-charts** - Angular Chart.js wrapper
- **RxJS** - Reactive programming

## Development

```bash
# Install dependencies
npm ci

# Start dev server
npm run start

# Build for production
npm run build

# Run tests
npm test
```

### Chat Interface (`/chat`)
- **Dual Mode**: Switch between active runs and gem chat.
- **Gems Navigation**: Sidebar integration for accessing specialized AI personas.
- **Interactive Dialogue**: Direct chat with typing indicators and persona-specific branding.

## Routing

- `/` → Redirects to `/chat`
- `/chat` → Active run or new run creation
- `/chat/gem/:persona` → Direct chat with a specific Gem
- `/dashboard` → Dashboard home
- `/runs` → Run list with filters
- `/runs/:id` → Run detail view
- `/analytics` → Analytics dashboard

## Styling

- Glass morphism design system
- Dark theme with accent colors
- Responsive grid layouts
- Custom scrollbars
- Status-based color coding:
  - Green: Success/Done
  - Red: Failed
  - Yellow: Escalated/Warning
  - Blue: In Progress/Info
