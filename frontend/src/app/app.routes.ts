import { Routes } from '@angular/router';
import { DashboardHomeComponent } from './components/dashboard-home.component';
import { RunListComponent } from './components/run-list.component';
import { RunDetailComponent } from './components/run-detail.component';
import { AnalyticsDashboardComponent } from './components/analytics-dashboard.component';
import { ChatDashboardComponent } from './components/index';
import { EscalationPanelComponent } from './components/oversight/escalation-panel.component';
import { ActivityLogComponent } from './components/activity-log.component';
import { WorkReportComponent } from './components/work-report.component';
import { AgentConfigComponent } from './components/config/agent-config.component';
import { OversightConfigComponent } from './components/config/oversight-config.component';
import { WaterfallTraceComponent } from './components/trace/waterfall-trace.component';
import { A2ARegistryComponent } from './components/a2a-registry.component';
import { GraftManagementComponent } from './components/graft-management.component';

export const routes: Routes = [
  { path: '', redirectTo: '/chat', pathMatch: 'full' },
  { path: 'chat', component: ChatDashboardComponent },
  { path: 'chat/:id', component: ChatDashboardComponent },
  { path: 'chat/gem/:persona', component: ChatDashboardComponent },
  { path: 'dashboard', component: DashboardHomeComponent },
  { path: 'runs', component: RunListComponent },
  { path: 'runs/:id', component: RunDetailComponent },
  { path: 'runs/:id/log', component: ActivityLogComponent },
  { path: 'runs/:id/report', component: WorkReportComponent },
  { path: 'runs/:id/trace', component: WaterfallTraceComponent },
  { path: 'oversight', component: EscalationPanelComponent },
  { path: 'analytics', component: AnalyticsDashboardComponent },
  { path: 'config/agents', component: AgentConfigComponent },
  { path: 'config/oversight', component: OversightConfigComponent },
  { path: 'a2a', component: A2ARegistryComponent },
  { path: 'grafts', component: GraftManagementComponent },
  { path: 'grafts/:runId', component: GraftManagementComponent }
];
