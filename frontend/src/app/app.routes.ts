import { Routes } from '@angular/router';
import { DashboardHomeComponent } from './components/dashboard-home.component';
import { RunListComponent } from './components/run-list.component';
import { RunDetailComponent } from './components/run-detail.component';
import { AnalyticsDashboardComponent } from './components/analytics-dashboard.component';
import { FailureAnalyticsComponent } from './components/failure-analytics.component';
import { ChatDashboardComponent } from './components/index';
import { EscalationPanelComponent } from './components/oversight/escalation-panel.component';
import { OversightInboxComponent } from './components/oversight/oversight-inbox.component';
import { ActivityLogComponent } from './components/activity-log.component';
import { WorkReportComponent } from './components/work-report.component';
import { AgentConfigComponent } from './components/config/agent-config.component';
import { OversightConfigComponent } from './components/config/oversight-config.component';
import { WaterfallTraceComponent } from './components/trace/waterfall-trace.component';
import { A2ARegistryComponent } from './components/a2a-registry.component';
import { GraftManagementComponent } from './components/graft-management.component';
import { OnboardingFlowComponent } from './components/onboarding-flow.component';
import { SettingsDashboardComponent } from './components/settings-dashboard.component';
import { OAuth2CallbackComponent } from './components/oauth2-callback.component';
import { UserProfileComponent } from './components/user-profile.component';
import { LoginComponent } from './components/login.component';
import { RegisterComponent } from './components/register.component';
import { ForgotPasswordComponent } from './components/forgot-password.component';
import { ResetPasswordComponent } from './components/reset-password.component';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/chat', pathMatch: 'full' },
  { path: 'auth/login', component: LoginComponent },
  { path: 'auth/register', component: RegisterComponent },
  { path: 'auth/forgot-password', component: ForgotPasswordComponent },
  { path: 'auth/reset-password', component: ResetPasswordComponent },
  { path: 'auth/callback', component: OAuth2CallbackComponent },
  { path: 'onboarding', component: OnboardingFlowComponent },
  { path: 'chat', component: ChatDashboardComponent },
  { path: 'chat/:id', component: ChatDashboardComponent },
  { path: 'chat/gem/:persona', component: ChatDashboardComponent },
  { path: 'dashboard', component: DashboardHomeComponent, canActivate: [authGuard] },
  { path: 'runs', component: RunListComponent, canActivate: [authGuard] },
  { path: 'runs/:id', component: RunDetailComponent, canActivate: [authGuard] },
  { path: 'runs/:id/log', component: ActivityLogComponent, canActivate: [authGuard] },
  { path: 'runs/:id/report', component: WorkReportComponent, canActivate: [authGuard] },
  { path: 'runs/:id/trace', component: WaterfallTraceComponent, canActivate: [authGuard] },
  { path: 'oversight', component: EscalationPanelComponent, canActivate: [authGuard] },
  { path: 'oversight/inbox', component: OversightInboxComponent, canActivate: [authGuard] },
  { path: 'analytics', component: AnalyticsDashboardComponent },
  { path: 'analytics/failures', component: FailureAnalyticsComponent },
  { path: 'config/agents', component: AgentConfigComponent },
  { path: 'config/oversight', component: OversightConfigComponent },
  { path: 'a2a', component: A2ARegistryComponent },
  { path: 'grafts', component: GraftManagementComponent },
  { path: 'grafts/:runId', component: GraftManagementComponent },
  { path: 'settings', component: SettingsDashboardComponent, canActivate: [authGuard] },
  { path: 'profile', component: UserProfileComponent, canActivate: [authGuard] }
];
