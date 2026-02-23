import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';

(window as any).global = window;

console.log('ATLASIA_DOCKER_SYNC_CHECK_V3');

bootstrapApplication(App, appConfig)
  .catch((err) => console.error(err));
