import 'zone.js';
import 'zone.js/testing';
import { TestBed, getTestBed } from '@angular/core/testing';
import {
  BrowserDynamicTestingModule,
  platformBrowserDynamicTesting
} from '@angular/platform-browser-dynamic/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { AuthService } from './auth.service';
import { describe, it, expect, beforeEach } from 'vitest';

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(() => {
    // Manually init environment if not already done
    const testBed = getTestBed();
    if (!testBed.platform) {
      testBed.initTestEnvironment(
        BrowserDynamicTestingModule,
        platformBrowserDynamicTesting()
      );
    }

    TestBed.configureTestingModule({
      providers: [
        AuthService,
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([])
      ]
    });
    service = TestBed.inject(AuthService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should have token management methods', () => {
    expect(service.getAccessToken).toBeDefined();
    expect(service.getRefreshToken).toBeDefined();
    expect(service.storeTokens).toBeDefined();
    expect(service.clearTokens).toBeDefined();
  });

  it('should manage tokens in localStorage', () => {
    const accessToken = 'test-access-token';
    const refreshToken = 'test-refresh-token';

    service.storeTokens(accessToken, refreshToken);
    expect(service.getAccessToken()).toBe(accessToken);
    expect(service.getRefreshToken()).toBe(refreshToken);

    service.clearTokens();
    expect(service.getAccessToken()).toBeNull();
    expect(service.getRefreshToken()).toBeNull();
  });

  it('should have authentication methods', () => {
    expect(service.login).toBeDefined();
    expect(service.logout).toBeDefined();
    expect(service.refreshToken).toBeDefined();
    expect(service.getUserProfile).toBeDefined();
  });
});
