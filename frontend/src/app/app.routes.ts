import { Routes } from '@angular/router';
import { DashboardHomeComponent } from './components/dashboard-home.component';
import { RunListComponent } from './components/run-list.component';
import { RunDetailComponent } from './components/run-detail.component';
import { AnalyticsDashboardComponent } from './components/analytics-dashboard.component';
import { ChatDashboardComponent } from './components/index';

export const routes: Routes = [
  { path: '', redirectTo: '/chat', pathMatch: 'full' },
  { path: 'chat', component: ChatDashboardComponent },
  { path: 'chat/:id', component: ChatDashboardComponent },
  { path: 'dashboard', component: DashboardHomeComponent },
  { path: 'runs', component: RunListComponent },
  { path: 'runs/:id', component: RunDetailComponent },
  { path: 'analytics', component: AnalyticsDashboardComponent }
];
