import { Routes } from '@angular/router';
import { DashboardHomeComponent } from './components/dashboard-home.component';
import { RunListComponent } from './components/run-list.component';
import { RunDetailComponent } from './components/run-detail.component';
import { AnalyticsDashboardComponent } from './components/analytics-dashboard.component';
import { ChatInterfaceComponent } from './components/chat-interface';

export const routes: Routes = [
  { path: '', redirectTo: '/chat', pathMatch: 'full' },
  { path: 'chat', component: ChatInterfaceComponent },
  { path: 'dashboard', component: DashboardHomeComponent },
  { path: 'runs', component: RunListComponent },
  { path: 'runs/:id', component: RunDetailComponent },
  { path: 'analytics', component: AnalyticsDashboardComponent }
];
