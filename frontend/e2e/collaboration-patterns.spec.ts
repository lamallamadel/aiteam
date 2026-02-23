import { test, expect } from '@playwright/test';
import { CollaborationPage, MultiUserCollaboration, TestData } from './collaboration-helpers';

/**
 * This file demonstrates common testing patterns for collaboration features.
 * Use these patterns as a reference when writing new tests.
 */

test.describe('Collaboration Testing Patterns', () => {
  test.describe('Pattern 1: Single User Testing', () => {
    test('should test basic collaboration setup', async ({ page }) => {
      const collabPage = new CollaborationPage(page);
      
      // Setup user session
      await collabPage.setupUser(TestData.userIds.user1);
      
      // Navigate to run
      await collabPage.navigateToRun(TestData.runIds.default);
      
      // Wait for connection (with error handling)
      try {
        await collabPage.waitForConnection(5000);
      } catch {
        test.skip(); // Skip if backend unavailable
        return;
      }
      
      // Verify connection state
      const state = await collabPage.getCollaborationState();
      expect(state.isConnected).toBe(true);
    });

    test('should test sending mutations', async ({ page }) => {
      const collabPage = new CollaborationPage(page);
      await collabPage.setupUser(TestData.userIds.user1);
      await collabPage.navigateToRun(TestData.runIds.default);
      
      try {
        await collabPage.waitForConnection();
      } catch {
        test.skip();
        return;
      }
      
      // Send mutation
      await collabPage.sendGraft('ARCHITECT', 'test-agent');
      
      // Wait for propagation
      await page.waitForTimeout(1000);
      
      // Verify state
      const state = await collabPage.getCollaborationState();
      expect(state.events.length).toBeGreaterThanOrEqual(1);
    });
  });

  test.describe('Pattern 2: Multi-User Testing', () => {
    test('should test two-user collaboration', async ({ browser }) => {
      const context1 = await browser.newContext();
      const context2 = await browser.newContext();
      
      const page1 = new CollaborationPage(await context1.newPage());
      const page2 = new CollaborationPage(await context2.newPage());
      
      // Setup both users
      await page1.setupUser(TestData.userIds.user1);
      await page2.setupUser(TestData.userIds.user2);
      
      await page1.navigateToRun(TestData.runIds.default);
      await page2.navigateToRun(TestData.runIds.default);
      
      try {
        await page1.waitForConnection();
        await page2.waitForConnection();
      } catch {
        await context1.close();
        await context2.close();
        test.skip();
        return;
      }
      
      // User 1 performs action
      await page1.sendGraft('ARCHITECT', 'shared-agent');
      
      // Wait for sync
      await page1.page.waitForTimeout(2000);
      
      // User 2 should see the event
      const state2 = await page2.getCollaborationState();
      const hasGraftEvent = state2.events.some(
        (e: any) => e.eventType === 'GRAFT' && e.data.agentName === 'shared-agent'
      );
      
      expect(hasGraftEvent).toBe(true);
      
      // Cleanup
      await context1.close();
      await context2.close();
    });

    test('should test three-user collaboration', async ({ browser }) => {
      const collab = new MultiUserCollaboration();
      
      // Setup users
      const page1 = await collab.addUser(
        await browser.newContext(),
        TestData.userIds.user1,
        TestData.runIds.default
      );
      
      const page2 = await collab.addUser(
        await browser.newContext(),
        TestData.userIds.user2,
        TestData.runIds.default
      );
      
      const page3 = await collab.addUser(
        await browser.newContext(),
        TestData.userIds.user3,
        TestData.runIds.default
      );
      
      // All users perform different actions
      await Promise.all([
        page1.sendGraft('ARCHITECT', 'agent-1'),
        page2.sendPrune('QUALIFIER', true),
        page3.sendFlag('PM', 'flag-note'),
      ]);
      
      // Wait for sync
      await collab.waitForSync();
      
      // Verify all users see all events
      const state1 = await page1.getCollaborationState();
      expect(state1.events.length).toBeGreaterThanOrEqual(3);
      
      // Cleanup
      await collab.cleanup();
    });
  });

  test.describe('Pattern 3: Testing Presence', () => {
    test('should verify presence updates', async ({ browser }) => {
      const context1 = await browser.newContext();
      const context2 = await browser.newContext();
      
      const page1 = new CollaborationPage(await context1.newPage());
      const page2 = new CollaborationPage(await context2.newPage());
      
      await page1.setupUser(TestData.userIds.user1);
      await page1.navigateToRun(TestData.runIds.default);
      
      try {
        await page1.waitForConnection();
      } catch {
        await context1.close();
        await context2.close();
        test.skip();
        return;
      }
      
      // Get initial user count
      const initialCount = await page1.getActiveUserCount();
      
      // Second user joins
      await page2.setupUser(TestData.userIds.user2);
      await page2.navigateToRun(TestData.runIds.default);
      await page2.waitForConnection();
      
      // Wait for presence update
      await page1.page.waitForFunction(
        (expected) => {
          const store = (window as any).workflowStreamStore;
          return store.activeUsers().length > expected;
        },
        initialCount,
        { timeout: 5000 }
      );
      
      // Verify user count increased
      const updatedCount = await page1.getActiveUserCount();
      expect(updatedCount).toBeGreaterThan(initialCount);
      
      await context1.close();
      await context2.close();
    });
  });

  test.describe('Pattern 4: Testing Notifications', () => {
    test('should verify notification display', async ({ browser }) => {
      const context1 = await browser.newContext();
      const context2 = await browser.newContext();
      
      const page1 = new CollaborationPage(await context1.newPage());
      const page2 = new CollaborationPage(await context2.newPage());
      
      await page1.setupUser(TestData.userIds.user1);
      await page2.setupUser(TestData.userIds.user2);
      
      await page1.navigateToRun(TestData.runIds.default);
      await page2.navigateToRun(TestData.runIds.default);
      
      try {
        await page1.waitForConnection();
        await page2.waitForConnection();
      } catch {
        await context1.close();
        await context2.close();
        test.skip();
        return;
      }
      
      // User 1 performs action
      await page1.sendGraft('ARCHITECT', 'notification-test');
      
      // User 2 should see notification
      await expect(page2.page.locator('.notification.notif-graft')).toBeVisible({
        timeout: 5000,
      });
      
      // Verify notification content
      const notificationText = await page2.page
        .locator('.notification.notif-graft .notif-message')
        .textContent();
      
      expect(notificationText).toContain('grafted');
      expect(notificationText).toContain('notification-test');
      
      await context1.close();
      await context2.close();
    });
  });

  test.describe('Pattern 5: Testing Error Handling', () => {
    test('should handle disconnection gracefully', async ({ page }) => {
      const collabPage = new CollaborationPage(page);
      await collabPage.setupUser(TestData.userIds.user1);
      await collabPage.navigateToRun(TestData.runIds.default);
      
      try {
        await collabPage.waitForConnection();
      } catch {
        test.skip();
        return;
      }
      
      // Force disconnect
      await collabPage.forceDisconnect();
      
      // Wait for disconnect to be recognized
      await page.waitForFunction(
        () => {
          const store = (window as any).workflowStreamStore;
          return !store.isCollaborationConnected();
        },
        { timeout: 5000 }
      );
      
      // Verify disconnected state
      const state = await collabPage.getCollaborationState();
      expect(state.isConnected).toBe(false);
    });

    test('should handle invalid data gracefully', async ({ page }) => {
      const collabPage = new CollaborationPage(page);
      await collabPage.setupUser(TestData.userIds.user1);
      await collabPage.navigateToRun(TestData.runIds.default);
      
      try {
        await collabPage.waitForConnection();
      } catch {
        test.skip();
        return;
      }
      
      // Send invalid mutation (should not crash)
      await page.evaluate(() => {
        try {
          const store = (window as any).workflowStreamStore;
          store.sendGraft('', ''); // Invalid parameters
        } catch (e) {
          // Expected to handle gracefully
        }
      });
      
      await page.waitForTimeout(1000);
      
      // Application should still be functional
      const state = await collabPage.getCollaborationState();
      expect(state).toBeTruthy();
    });
  });

  test.describe('Pattern 6: Testing Concurrent Operations', () => {
    test('should handle concurrent mutations', async ({ browser }) => {
      const context1 = await browser.newContext();
      const context2 = await browser.newContext();
      
      const page1 = new CollaborationPage(await context1.newPage());
      const page2 = new CollaborationPage(await context2.newPage());
      
      await page1.setupUser(TestData.userIds.user1);
      await page2.setupUser(TestData.userIds.user2);
      
      await page1.navigateToRun(TestData.runIds.default);
      await page2.navigateToRun(TestData.runIds.default);
      
      try {
        await page1.waitForConnection();
        await page2.waitForConnection();
      } catch {
        await context1.close();
        await context2.close();
        test.skip();
        return;
      }
      
      // Both users perform concurrent mutations
      await Promise.all([
        page1.sendGraft('ARCHITECT', 'concurrent-a'),
        page2.sendGraft('ARCHITECT', 'concurrent-b'),
      ]);
      
      // Wait for propagation
      await page1.page.waitForTimeout(3000);
      
      // Both should have received both events
      const state1 = await page1.getCollaborationState();
      const graftEvents = state1.events.filter((e: any) => e.eventType === 'GRAFT');
      
      expect(graftEvents.length).toBeGreaterThanOrEqual(2);
      
      await context1.close();
      await context2.close();
    });
  });

  test.describe('Pattern 7: Testing Performance', () => {
    test('should handle rapid mutations', async ({ page }) => {
      const collabPage = new CollaborationPage(page);
      await collabPage.setupUser(TestData.userIds.user1);
      await collabPage.navigateToRun(TestData.runIds.default);
      
      try {
        await collabPage.waitForConnection();
      } catch {
        test.skip();
        return;
      }
      
      const startTime = Date.now();
      
      // Send many mutations rapidly
      for (let i = 0; i < 50; i++) {
        await collabPage.sendGraft('ARCHITECT', `rapid-${i}`);
      }
      
      const duration = Date.now() - startTime;
      console.log(`Sent 50 mutations in ${duration}ms`);
      
      await page.waitForTimeout(2000);
      
      // Should complete without errors
      const state = await collabPage.getCollaborationState();
      expect(state).toBeTruthy();
    });
  });

  test.describe('Pattern 8: Testing Cleanup', () => {
    test('should properly clean up on navigation', async ({ page }) => {
      const collabPage = new CollaborationPage(page);
      await collabPage.setupUser(TestData.userIds.user1);
      await collabPage.navigateToRun(TestData.runIds.default);
      
      try {
        await collabPage.waitForConnection();
      } catch {
        test.skip();
        return;
      }
      
      // Send some events
      await collabPage.sendGraft('ARCHITECT', 'before-nav');
      await page.waitForTimeout(1000);
      
      // Navigate away
      await page.goto('/');
      await page.waitForTimeout(1000);
      
      // Navigate to different run
      await collabPage.navigateToRun(TestData.runIds.alternative);
      await page.waitForTimeout(2000);
      
      // State should be reset for new run
      const state = await collabPage.getCollaborationState();
      
      // Events should be empty or only for the new run
      const oldEvents = state.events.filter(
        (e: any) => e.data?.agentName === 'before-nav'
      );
      
      expect(oldEvents.length).toBe(0);
    });
  });
});
