import 'zone.js';
import 'zone.js/testing';
import { TestBed, getTestBed } from '@angular/core/testing';
import {
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting
} from '@angular/platform-browser-dynamic/testing';
import { App } from './app';
import { OrchestratorService } from './services/orchestrator.service';
import { EscalationService } from './services/escalation.service';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { describe, it, expect, beforeEach, vi } from 'vitest';

describe('App', () => {
  let orchestratorServiceMock: any;
  let escalationServiceMock: any;

  beforeEach(async () => {
    // Manually init environment if not already done
    const testBed = getTestBed();
    if (!testBed.platform) {
      testBed.initTestEnvironment(
        BrowserDynamicTestingModule,
        platformBrowserDynamicTesting()
      );
    }

    orchestratorServiceMock = {
      getPersonas: vi.fn().mockReturnValue(of([])),
      getPendingInterrupts: vi.fn().mockReturnValue(of([]))
    };
    escalationServiceMock = {};

    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        { provide: OrchestratorService, useValue: orchestratorServiceMock },
        { provide: EscalationService, useValue: escalationServiceMock },
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    }).compileComponents();
  });

  it('should create the app', () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app).toBeTruthy();
  });

  it(`should have the 'Atlasia Orchestrator' title`, () => {
    const fixture = TestBed.createComponent(App);
    const app = fixture.componentInstance;
    expect(app['title']()).toEqual('Atlasia Orchestrator');
  });
});
