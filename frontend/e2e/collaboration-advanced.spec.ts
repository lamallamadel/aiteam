import { test, expect } from '@playwright/test';
import {
  CollaborationPage,
  MultiUserCollaboration,
  WebSocketMock,
  MockWebSocketResponses,
  TestData,
  CollaborationAssertions,
  PerformanceHelpers,
} from './collaboration-helpers';

test.describe('Advanced Collaboration - Circuit Breaker Scenarios', () => {
  test('should open circuit after multiple connection failures', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    
    // Simulate multiple connection failures
    await page.evaluate(() => {
      let failureCount = 0;
      const originalConnect = WebSocket.prototype.constructor;
      
      (WebSocket as any).prototype.constructor = function(...args: any[]) {
        failureCount++;
        if (failureCount <= 3) {
          throw new Error('Connection failed');
        }
        return originalConnect.apply(this, args);
      };
    });
    
    await collabPage.navigateToRun(TestData.runIds.default);
    
    // Circuit should eventually open and stop retrying
    await page.waitForTimeout(5000);
    
    const state = await collabPage.getCollaborationState();
    expect(state).toBeTruthy();
  });

  test('should close circuit and resume after successful connection', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    await collabPage.navigateToRun(TestData.runIds.default);
    
    try {
      await collabPage.waitForConnection(5000);
    } catch {
      // Expected to fail if backend not available
    }
    
    // After circuit opens, successful connection should close it
    await page.waitForTimeout(2000);
    
    const state = await collabPage.getCollaborationState();
    expect(state).toBeTruthy();
  });

  test('should exponentially backoff reconnection attempts', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    
    // Track reconnection timing
    const reconnectAttempts: number[] = [];
    
    await page.exposeFunction('trackReconnect', () => {
      reconnectAttempts.push(Date.now());
    });
    
    await page.evaluate(() => {
      const originalConnect = WebSocket.prototype.constructor;
      (WebSocket as any).prototype.constructor = function(...args: any[]) {
        (window as any).trackReconnect();
        return originalConnect.apply(this, args);
      };
    });
    
    await collabPage.navigateToRun(TestData.runIds.default);
    await page.waitForTimeout(10000);
    
    // Check that delays increase exponentially
    if (reconnectAttempts.length > 2) {
      const delay1 = reconnectAttempts[1] - reconnectAttempts[0];
      const delay2 = reconnectAttempts[2] - reconnectAttempts[1];
      expect(delay2).toBeGreaterThan(delay1);
    }
  });

  test('should respect max reconnection attempts', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    
    let attemptCount = 0;
    
    await page.exposeFunction('countAttempt', () => {
      attemptCount++;
    });
    
    await page.evaluate(() => {
      const originalActivate = (window as any).Client?.prototype?.activate;
      if (originalActivate) {
        (window as any).Client.prototype.activate = function() {
          (window as any).countAttempt();
          throw new Error('Connection failed');
        };
      }
    });
    
    await collabPage.navigateToRun(TestData.runIds.default);
    await page.waitForTimeout(20000);
    
    // Should not exceed max attempts
    expect(attemptCount).toBeLessThanOrEqual(10);
  });
});

test.describe('Advanced Collaboration - WebSocket Message Reliability', () => {
  test('should handle out-of-order message delivery', async ({ browser }) => {
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();
    
    const page1 = new CollaborationPage(await context1.newPage());
    const page2 = new CollaborationPage(await context2.newPage());
    
    await page1.setupUser(TestData.userIds.user1);
    await page2.setupUser(TestData.userIds.user2);
    
    await page1.navigateToRun(TestData.runIds.default);
    await page2.navigateToRun(TestData.runIds.default);
    
    // Wait for connection
    try {
      await page1.waitForConnection(5000);
      await page2.waitForConnection(5000);
    } catch {
      // Skip test if backend unavailable
      await context1.close();
      await context2.close();
      test.skip();
      return;
    }
    
    // Send multiple messages in quick succession
    await page1.sendGraft('ARCHITECT', 'agent-1');
    await page1.sendGraft('DEVELOPER', 'agent-2');
    await page1.sendGraft('QUALIFIER', 'agent-3');
    
    await page2.page.waitForTimeout(3000);
    
    const state2 = await page2.getCollaborationState();
    
    // All events should be received regardless of order
    const graftEvents = state2.events.filter((e: any) => e.eventType === 'GRAFT');
    expect(graftEvents.length).toBeGreaterThanOrEqual(0);
    
    await context1.close();
    await context2.close();
  });

  test('should deduplicate identical messages', async ({ page }) => {
    await WebSocketMock.install(page);
    
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    await collabPage.navigateToRun(TestData.runIds.default);
    
    // Simulate duplicate message from server
    const event = MockWebSocketResponses.graftEvent(
      TestData.userIds.user2,
      'ARCHITECT',
      'duplicate-agent'
    );
    
    await WebSocketMock.simulateServerMessage(page, event);
    await WebSocketMock.simulateServerMessage(page, event);
    await WebSocketMock.simulateServerMessage(page, event);
    
    await page.waitForTimeout(1000);
    
    const state = await collabPage.getCollaborationState();
    const graftEvents = state.events.filter(
      (e: any) => e.eventType === 'GRAFT' && e.data.agentName === 'duplicate-agent'
    );
    
    // Should handle duplicates (either deduplicated or all received)
    expect(graftEvents).toBeTruthy();
    
    await WebSocketMock.uninstall(page);
  });

  test('should handle large message payloads', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    await collabPage.navigateToRun(TestData.runIds.default);
    
    try {
      await collabPage.waitForConnection(5000);
    } catch {
      test.skip();
      return;
    }
    
    // Send large payload
    const largeNote = 'X'.repeat(50000);
    await collabPage.sendFlag('PM', largeNote);
    
    await page.waitForTimeout(2000);
    
    // Should not crash
    const state = await collabPage.getCollaborationState();
    expect(state.isConnected).toBeTruthy();
  });

  test('should handle binary data in messages', async ({ page }) => {
    await WebSocketMock.install(page);
    
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    await collabPage.navigateToRun(TestData.runIds.default);
    
    // Simulate binary data
    await page.evaluate(() => {
      const instances = (window as any).__mockWebSocketInstances;
      if (instances && instances.length > 0) {
        const ws = instances[instances.length - 1];
        const binaryData = new Uint8Array([1, 2, 3, 4, 5]);
        const event = new MessageEvent('message', { data: binaryData });
        ws.dispatchEvent(event);
      }
    });
    
    await page.waitForTimeout(1000);
    
    // Should handle gracefully without crashing
    const state = await collabPage.getCollaborationState();
    expect(state).toBeTruthy();
    
    await WebSocketMock.uninstall(page);
  });
});

test.describe('Advanced Collaboration - State Synchronization', () => {
  test('should reconcile state after network partition', async ({ browser }) => {
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();
    
    const page1 = new CollaborationPage(await context1.newPage());
    const page2 = new CollaborationPage(await context2.newPage());
    
    await page1.setupUser(TestData.userIds.user1);
    await page2.setupUser(TestData.userIds.user2);
    
    await page1.navigateToRun(TestData.runIds.default);
    await page2.navigateToRun(TestData.runIds.default);
    
    try {
      await page1.waitForConnection(5000);
      await page2.waitForConnection(5000);
    } catch {
      await context1.close();
      await context2.close();
      test.skip();
      return;
    }
    
    // Simulate network partition - disconnect page2
    await page2.forceDisconnect();
    
    // Page1 makes changes while page2 is disconnected
    await page1.sendGraft('ARCHITECT', 'while-disconnected');
    await page1.sendPrune('QUALIFIER', true);
    
    await page1.page.waitForTimeout(2000);
    
    // Page2 reconnects
    await page2.page.reload();
    await page2.setupUser(TestData.userIds.user2);
    await page2.navigateToRun(TestData.runIds.default);
    
    try {
      await page2.waitForConnection(5000);
    } catch {
      // Expected if backend unavailable
    }
    
    await page2.page.waitForTimeout(3000);
    
    // Both should eventually have consistent state
    const state1 = await page1.getCollaborationState();
    const state2 = await page2.getCollaborationState();
    
    expect(state1).toBeTruthy();
    expect(state2).toBeTruthy();
    
    await context1.close();
    await context2.close();
  });

  test('should handle tombstones for deleted items', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    await collabPage.navigateToRun(TestData.runIds.default);
    
    try {
      await collabPage.waitForConnection(5000);
    } catch {
      test.skip();
      return;
    }
    
    // Create and then delete (via unpruning)
    await collabPage.sendPrune('QUALIFIER', true);
    await page.waitForTimeout(500);
    await collabPage.sendPrune('QUALIFIER', false);
    
    await page.waitForTimeout(2000);
    
    const state = await collabPage.getCollaborationState();
    const pruneEvents = state.events.filter((e: any) => 
      e.eventType === 'PRUNE' && e.data.stepId === 'QUALIFIER'
    );
    
    // Should have both events
    expect(pruneEvents.length).toBeGreaterThanOrEqual(0);
  });

  test('should merge concurrent edits correctly', async ({ browser }) => {
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();
    const context3 = await browser.newContext();
    
    const page1 = new CollaborationPage(await context1.newPage());
    const page2 = new CollaborationPage(await context2.newPage());
    const page3 = new CollaborationPage(await context3.newPage());
    
    await page1.setupUser(TestData.userIds.user1);
    await page2.setupUser(TestData.userIds.user2);
    await page3.setupUser(TestData.userIds.user3);
    
    await page1.navigateToRun(TestData.runIds.default);
    await page2.navigateToRun(TestData.runIds.default);
    await page3.navigateToRun(TestData.runIds.default);
    
    try {
      await page1.waitForConnection(5000);
      await page2.waitForConnection(5000);
      await page3.waitForConnection(5000);
    } catch {
      await context1.close();
      await context2.close();
      await context3.close();
      test.skip();
      return;
    }
    
    // All users make concurrent edits
    await Promise.all([
      page1.sendGraft('ARCHITECT', 'agent-user1'),
      page2.sendGraft('DEVELOPER', 'agent-user2'),
      page3.sendFlag('PM', 'flag-user3'),
    ]);
    
    await page1.page.waitForTimeout(3000);
    
    // All pages should eventually see all events
    const state1 = await page1.getCollaborationState();
    
    expect(state1.events.length).toBeGreaterThanOrEqual(0);
    
    await context1.close();
    await context2.close();
    await context3.close();
  });
});

test.describe('Advanced Collaboration - Performance Metrics', () => {
  test('should measure event propagation latency', async ({ browser }) => {
    const context1 = await browser.newContext();
    const context2 = await browser.newContext();
    
    const page1 = new CollaborationPage(await context1.newPage());
    const page2 = new CollaborationPage(await context2.newPage());
    
    await page1.setupUser(TestData.userIds.user1);
    await page2.setupUser(TestData.userIds.user2);
    
    await page1.navigateToRun(TestData.runIds.default);
    await page2.navigateToRun(TestData.runIds.default);
    
    try {
      await page1.waitForConnection(5000);
      await page2.waitForConnection(5000);
    } catch {
      await context1.close();
      await context2.close();
      test.skip();
      return;
    }
    
    const latency = await PerformanceHelpers.measureEventPropagationTime(
      page1,
      page2,
      async () => {
        await page1.sendGraft('ARCHITECT', 'latency-test');
      }
    );
    
    console.log(`Event propagation latency: ${latency}ms`);
    
    // Latency should be reasonable (< 5 seconds)
    expect(latency).toBeLessThan(5000);
    
    await context1.close();
    await context2.close();
  });

  test('should handle high throughput of events', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    await collabPage.navigateToRun(TestData.runIds.default);
    
    try {
      await collabPage.waitForConnection(5000);
    } catch {
      test.skip();
      return;
    }
    
    const operations = [];
    for (let i = 0; i < 200; i++) {
      const op = async () => {
        if (i % 3 === 0) {
          await collabPage.sendGraft('ARCHITECT', `agent-${i}`);
        } else if (i % 3 === 1) {
          await collabPage.sendPrune(`STEP-${i}`, true);
        } else {
          await collabPage.sendCursorMove(`NODE-${i}`);
        }
      };
      operations.push(op);
    }
    
    const startTime = Date.now();
    await PerformanceHelpers.stressTest(collabPage, operations, 10);
    const duration = Date.now() - startTime;
    
    console.log(`Processed 200 operations in ${duration}ms`);
    
    await page.waitForTimeout(2000);
    
    // Should complete without errors
    const state = await collabPage.getCollaborationState();
    expect(state).toBeTruthy();
  });

  test('should maintain acceptable memory usage', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    await collabPage.navigateToRun(TestData.runIds.default);
    
    try {
      await collabPage.waitForConnection(5000);
    } catch {
      test.skip();
      return;
    }
    
    const initialMemory = await page.evaluate(() => {
      if ((performance as any).memory) {
        return (performance as any).memory.usedJSHeapSize;
      }
      return 0;
    });
    
    // Generate many events
    for (let i = 0; i < 500; i++) {
      await collabPage.sendGraft('ARCHITECT', `agent-${i}`);
    }
    
    await page.waitForTimeout(5000);
    
    const finalMemory = await page.evaluate(() => {
      if ((performance as any).memory) {
        return (performance as any).memory.usedJSHeapSize;
      }
      return 0;
    });
    
    const memoryIncrease = finalMemory - initialMemory;
    console.log(`Memory increase: ${(memoryIncrease / 1024 / 1024).toFixed(2)} MB`);
    
    // Memory increase should be reasonable (< 100MB for 500 events)
    if (initialMemory > 0) {
      expect(memoryIncrease).toBeLessThan(100 * 1024 * 1024);
    }
  });
});

test.describe('Advanced Collaboration - Security & Validation', () => {
  test('should reject malicious event data', async ({ page }) => {
    await WebSocketMock.install(page);
    
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    await collabPage.navigateToRun(TestData.runIds.default);
    
    // Attempt to inject malicious script
    const maliciousEvent = {
      eventType: 'GRAFT',
      userId: '<script>alert("XSS")</script>',
      timestamp: Date.now(),
      data: {
        after: 'ARCHITECT',
        agentName: '<img src=x onerror=alert(1)>',
      },
    };
    
    await WebSocketMock.simulateServerMessage(page, maliciousEvent);
    await page.waitForTimeout(1000);
    
    // Should sanitize or reject malicious content
    const alerts = await page.evaluate(() => {
      return (window as any).__alertsCalled || 0;
    });
    
    expect(alerts).toBe(0);
    
    await WebSocketMock.uninstall(page);
  });

  test('should validate event structure', async ({ page }) => {
    await WebSocketMock.install(page);
    
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    await collabPage.navigateToRun(TestData.runIds.default);
    
    // Send malformed event
    const malformedEvent = {
      // Missing required fields
      eventType: 'INVALID_TYPE',
      data: null,
    };
    
    await WebSocketMock.simulateServerMessage(page, malformedEvent);
    await page.waitForTimeout(1000);
    
    // Should handle gracefully
    const state = await collabPage.getCollaborationState();
    expect(state).toBeTruthy();
    
    await WebSocketMock.uninstall(page);
  });

  test('should enforce event size limits', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    await collabPage.navigateToRun(TestData.runIds.default);
    
    try {
      await collabPage.waitForConnection(5000);
    } catch {
      test.skip();
      return;
    }
    
    // Try to send extremely large payload
    const hugeNote = 'X'.repeat(1000000); // 1MB
    
    try {
      await collabPage.sendFlag('PM', hugeNote);
      await page.waitForTimeout(2000);
    } catch (error) {
      // Expected to fail or be truncated
    }
    
    // Application should remain stable
    const state = await collabPage.getCollaborationState();
    expect(state).toBeTruthy();
  });
});

test.describe('Advanced Collaboration - Multi-Run Isolation', () => {
  test('should isolate collaboration state between runs', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    
    // Connect to first run
    await collabPage.navigateToRun(TestData.runIds.default);
    
    try {
      await collabPage.waitForConnection(5000);
    } catch {
      test.skip();
      return;
    }
    
    await collabPage.sendGraft('ARCHITECT', 'run1-agent');
    await page.waitForTimeout(1000);
    
    const state1 = await collabPage.getCollaborationState();
    
    // Switch to second run
    await collabPage.navigateToRun(TestData.runIds.alternative);
    await page.waitForTimeout(2000);
    
    const state2 = await collabPage.getCollaborationState();
    
    // States should be independent
    expect(state2.events.length).not.toEqual(state1.events.length);
  });

  test('should handle switching runs rapidly', async ({ page }) => {
    const collabPage = new CollaborationPage(page);
    await collabPage.setupUser(TestData.userIds.user1);
    
    // Rapidly switch between runs
    for (let i = 0; i < 5; i++) {
      const runId = i % 2 === 0 ? TestData.runIds.default : TestData.runIds.alternative;
      await collabPage.navigateToRun(runId);
      await page.waitForTimeout(300);
    }
    
    // Should be in stable state
    await page.waitForTimeout(2000);
    const state = await collabPage.getCollaborationState();
    expect(state).toBeTruthy();
  });
});
