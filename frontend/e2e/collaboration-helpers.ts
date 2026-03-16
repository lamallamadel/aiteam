import { Page, BrowserContext } from '@playwright/test';

/**
 * Shared registry for cross-Playwright-context event bridging.
 * Partitioned by browser type so chromium/firefox/webkit workers never contaminate each other.
 * When a page registers, it gets an __e2eBroadcastEvent function that delivers
 * collaboration events to all other registered pages (same browser) via e2eInjectEvent.
 */
const _registryByBrowser = new Map<string, Set<Page>>();
/** Maps each page to its userId, used to inject USER_LEAVE when the page closes. */
const _pageUserIds = new Map<Page, string>();

function _getBrowserType(page: Page): string {
  return page.context().browser()?.browserType().name() ?? 'unknown';
}

function _getRegistry(page: Page): Set<Page> {
  const key = _getBrowserType(page);
  if (!_registryByBrowser.has(key)) {
    _registryByBrowser.set(key, new Set<Page>());
  }
  return _registryByBrowser.get(key)!;
}

/** Backward-compatible flat view of all registered pages (all browsers). */
export const e2ePageRegistry = new Set<Page>();

/** Injects an event into a target page and forces Angular CD. */
async function _injectEvent(target: Page, event: unknown): Promise<void> {
  await target.evaluate((evt: any) => {
    const svc = (window as any).collaborationService;
    if (!svc?.e2eInjectEvent) return;
    svc.e2eInjectEvent(evt);
    const notifEl = document.querySelector('app-collaboration-notifications');
    const ng = (window as any).ng;
    const comp = ng?.getComponent?.(notifEl);
    if (comp && ng?.applyChanges) ng.applyChanges(comp);
  }, event);
}

export async function registerPageForSync(page: Page): Promise<void> {
  const registry = _getRegistry(page);
  if (registry.has(page)) return;
  registry.add(page);
  e2ePageRegistry.add(page);

  // Track navigations to ensure the page stabilises before we try to inject events.
  // This listener is intentionally kept (even without logging) because the mere act of
  // attaching it prevents a race condition where `_injectEvent` runs before the page
  // has fully committed to a URL after initial load.
  page.on('framenavigated', () => {});

  // exposeFunction creates a Node.js-callable function that the Angular mock service invokes.
  // Deliveries run in PARALLEL so that stale/slow pages (e.g., from tests that skipped without
  // closing their contexts) never block delivery to the real target page.  Each delivery has a
  // 2-second timeout; if a page doesn't respond in time we skip it (keeping it in the registry
  // so the close-handler can remove it cleanly when it eventually closes).
  await page.exposeFunction('__e2eBroadcastEvent', async (event: unknown) => {
    // Capture userId from USER_JOIN so we can inject USER_LEAVE when this page closes.
    if ((event as any)?.eventType === 'USER_JOIN' && (event as any)?.userId) {
      _pageUserIds.set(page, (event as any).userId);
    }

    // Only deliver to pages in the SAME browser to prevent cross-worker contamination.
    // Snapshot the set first so concurrent mutations don't affect this iteration.
    const targets = [...registry].filter((t) => {
      if (t === page) return false; // skip self
      if (t.isClosed()) {
        registry.delete(t);
        e2ePageRegistry.delete(t);
        _pageUserIds.delete(t);
        return false;
      }
      return true;
    });

    await Promise.all(
      targets.map((target) =>
        Promise.race([
          _injectEvent(target, event),
          // 2-second per-target guard: stale pages from skipped tests must not
          // block delivery to the active page.
          new Promise<void>((resolve) => setTimeout(resolve, 2000)),
        ]).catch(() => {
          // Only remove from registry when the page is truly closed.
          if (target.isClosed()) {
            registry.delete(target);
            e2ePageRegistry.delete(target);
            _pageUserIds.delete(target);
          }
        })
      )
    );
  });

  page.on('close', () => {
    // Inject USER_LEAVE into all peer pages for this browser when this page closes.
    // We do this from Node.js because the page's JS context may already be destroyed.
    const userId = _pageUserIds.get(page);
    if (userId) {
      const peers = [...registry].filter((t) => t !== page && !t.isClosed());
      const leaveEvent = { eventType: 'USER_LEAVE', userId, timestamp: Date.now(), data: {} };
      for (const peer of peers) {
        _injectEvent(peer, leaveEvent).catch(() => {});
      }
    }
    registry.delete(page);
    e2ePageRegistry.delete(page);
    _pageUserIds.delete(page);
  });
}

/**
 * Test data generators for collaboration testing
 */
export const TestData = {
  runIds: {
    default: '550e8400-e29b-41d4-a716-446655440000',
    alternative: '650e8400-e29b-41d4-a716-446655440001',
  },
  
  userIds: {
    user1: 'user_test_001',
    user2: 'user_test_002',
    user3: 'user_test_003',
  },
  
  agentNames: [
    'ARCHITECT',
    'DEVELOPER',
    'QUALIFIER',
    'PM',
    'SRE',
    'CODE_REVIEWER',
    'SECURITY_ENGINEER',
  ],
  
  generateGraftData: (after: string, agentName: string) => ({
    after,
    agentName,
  }),
  
  generatePruneData: (stepId: string, isPruned: boolean) => ({
    stepId,
    isPruned,
  }),
  
  generateFlagData: (stepId: string, note: string) => ({
    stepId,
    note,
  }),
};

/**
 * Mock WebSocket server responses for testing
 */
export const MockWebSocketResponses = {
  graftEvent: (userId: string, after: string, agentName: string) => ({
    eventType: 'GRAFT',
    userId,
    timestamp: Date.now(),
    data: { after, agentName },
  }),
  
  pruneEvent: (userId: string, stepId: string, isPruned: boolean) => ({
    eventType: 'PRUNE',
    userId,
    timestamp: Date.now(),
    data: { stepId, isPruned },
  }),
  
  flagEvent: (userId: string, stepId: string, note: string) => ({
    eventType: 'FLAG',
    userId,
    timestamp: Date.now(),
    data: { stepId, note },
  }),
  
  userJoinEvent: (userId: string, activeUsers: string[]) => ({
    eventType: 'USER_JOIN',
    userId,
    timestamp: Date.now(),
    data: { activeUsers },
  }),
  
  userLeaveEvent: (userId: string, activeUsers: string[]) => ({
    eventType: 'USER_LEAVE',
    userId,
    timestamp: Date.now(),
    data: { activeUsers },
  }),
  
  cursorMoveEvent: (userId: string, nodeId: string, cursors: Record<string, string>) => ({
    eventType: 'CURSOR_MOVE',
    userId,
    timestamp: Date.now(),
    data: { nodeId, cursors },
  }),
};

/**
 * Page object for collaboration features
 */
export class CollaborationPage {
  constructor(public page: Page) {}
  
  async setupUser(userId: string, runId?: string): Promise<void> {
    await registerPageForSync(this.page);

    await this.page.addInitScript(() => {
      (window as any).__E2E_MOCK_COLLAB__ = true;
      (window as any).__E2E_FORCE_COLLAB_POLLING__ = true;
    });

    await this.page.route('**/api/auth/me*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: userId,
          username: userId,
          email: `${userId}@e2e.local`,
          roles: ['USER'],
        }),
      });
    });

    await this.page.route('**/api/auth/csrf*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({}),
        headers: { 'set-cookie': 'XSRF-TOKEN=e2e-token; Path=/' },
      });
    });

    // Mock token refresh so a transient 401 never triggers a login redirect.
    await this.page.route('**/api/auth/refresh*', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ accessToken: 'e2e-access-token', refreshToken: 'e2e-refresh-token' }),
      });
    });

    if (runId) {
      await this.page.route(`**/api/runs/${runId}`, async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({
            id: runId,
            repo: 'owner/repo',
            issueNumber: 1,
            status: 'DEVELOPER',
            createdAt: new Date().toISOString(),
            updatedAt: new Date().toISOString(),
            ciFixCount: 0,
            e2eFixCount: 0,
          }),
        });
      });

      await this.page.route(`**/api/runs/${runId}/artifacts*`, async (route) => {
        await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
      });

      await this.page.route(`**/api/runs/${runId}/environment*`, async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'text/plain',
          body: JSON.stringify({ lifecycle: 'ACTIVE', capturedAt: new Date().toISOString() }),
        });
      });

      await this.page.route(`**/api/runs/${runId}/collaboration/poll*`, async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ events: [], activeUsers: [], cursorPositions: {} }),
        });
      });

      await this.page.route(`**/api/runs/${runId}/collaboration/replay*`, async (route) => {
        await route.fulfill({
          status: 200,
          contentType: 'application/json',
          body: JSON.stringify({ events: [] }),
        });
      });
    }

    // Wildcard: any /api/runs/{id}/collaboration/* endpoint (poll, replay, etc.)
    await this.page.route('**/api/runs/*/collaboration/**', async (route) => {
      const url = route.request().url();
      const body = url.includes('/replay')
        ? JSON.stringify({ events: [] })
        : JSON.stringify({ events: [], activeUsers: [], cursorPositions: {} });
      await route.fulfill({ status: 200, contentType: 'application/json', body });
    });

    // Wildcard: any /api/runs/{id} single-run endpoint (returns a mock run object)
    await this.page.route('**/api/runs/*', async (route) => {
      const url = route.request().url();
      // Extract the run id from the URL
      const match = url.match(/\/api\/runs\/([^/?]+)/);
      const id = match ? match[1] : (runId ?? 'mock-run-id');
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id,
          repo: 'owner/repo',
          issueNumber: 1,
          status: 'DEVELOPER',
          createdAt: new Date().toISOString(),
          updatedAt: new Date().toISOString(),
          ciFixCount: 0,
          e2eFixCount: 0,
        }),
      });
    });

    await this.page.route('**/api/runs', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          runId
            ? [
                {
                  id: runId,
                  repo: 'owner/repo',
                  issueNumber: 1,
                  status: 'DEVELOPER',
                  createdAt: new Date().toISOString(),
                  updatedAt: new Date().toISOString(),
                  ciFixCount: 0,
                  e2eFixCount: 0,
                },
              ]
            : []
        ),
      });
    });

    await this.page.route('**/api/personas*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
    });

    await this.page.route('**/api/oversight/interrupts/pending*', async (route) => {
      await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify([]) });
    });

    await this.page.addInitScript((id) => {
      try {
        localStorage.setItem('atlasia_access_token', 'e2e-access-token');
        localStorage.setItem('atlasia_refresh_token', 'e2e-refresh-token');
        localStorage.setItem('atlasia_user_id', id);
        (window as any).__E2E_MOCK_COLLAB__ = true;
        (window as any).__E2E_FORCE_COLLAB_POLLING__ = true;
      } catch {
        // no-op: localStorage can be unavailable on about:blank
      }
    }, userId);

    await this.page.evaluate((id) => {
      try {
        localStorage.setItem('atlasia_access_token', 'e2e-access-token');
        localStorage.setItem('atlasia_refresh_token', 'e2e-refresh-token');
        localStorage.setItem('atlasia_user_id', id);
        (window as any).__E2E_MOCK_COLLAB__ = true;
        (window as any).__E2E_FORCE_COLLAB_POLLING__ = true;
      } catch {
        // no-op: value will still be set on next navigation via addInitScript
      }
    }, userId);
  }
  
  async navigateToRun(runId: string): Promise<void> {
    await this.page.goto(`/runs/${runId}`);
  }
  
  async waitForConnection(timeout: number = 10000): Promise<void> {
    await this.page.waitForFunction(
      () => {
        const store = (window as any).workflowStreamStore;
        return store && store.isCollaborationConnected();
      },
      { timeout }
    );
  }
  
  async getCollaborationState() {
    return await this.page.evaluate(() => {
      const store = (window as any).workflowStreamStore;
      const events = store?.collaborationEvents() || [];
      return {
        isConnected: store?.isCollaborationConnected() || false,
        activeUsers: store?.activeUsers() || [],
        events,
        cursorPositions: Array.from(store?.cursorPositions()?.entries() || []),
      };
    });
  }

  async sendGraft(after: string, agentName: string): Promise<void> {
    await this.page.evaluate(
      ({ after, agentName }) => {
        const store = (window as any).workflowStreamStore;
        store.sendGraft(after, agentName);
      },
      { after, agentName }
    );
  }
  
  async sendPrune(stepId: string, isPruned: boolean): Promise<void> {
    await this.page.evaluate(
      ({ stepId, isPruned }) => {
        const store = (window as any).workflowStreamStore;
        store.sendPrune(stepId, isPruned);
      },
      { stepId, isPruned }
    );
  }
  
  async sendFlag(stepId: string, note: string): Promise<void> {
    await this.page.evaluate(
      ({ stepId, note }) => {
        const store = (window as any).workflowStreamStore;
        store.sendFlag(stepId, note);
      },
      { stepId, note }
    );
  }
  
  async sendCursorMove(nodeId: string): Promise<void> {
    await this.page.evaluate(
      (nodeId) => {
        const store = (window as any).workflowStreamStore;
        store.sendCursorMove(nodeId);
      },
      nodeId
    );
  }
  
  async disconnect(): Promise<void> {
    await this.page.evaluate(() => {
      const service = (window as any).collaborationService;
      if (service) {
        service.disconnect();
      }
    });
  }
  
  async forceDisconnect(): Promise<void> {
    await this.page.evaluate(() => {
      const service = (window as any).collaborationService;
      if (service && service.client) {
        service.client.deactivate();
      }
    });
  }
  
  async waitForEvent(
    eventType: string,
    predicate?: (event: any) => boolean,
    timeout: number = 5000
  ): Promise<void> {
    await this.page.waitForFunction(
      ({ eventType, predicate }) => {
        const store = (window as any).workflowStreamStore;
        const events = store.collaborationEvents();
        const filtered = events.filter((e: any) => e.eventType === eventType);
        if (!predicate) {
          return filtered.length > 0;
        }
        return filtered.some((e: any) => {
          try {
            return eval(`(${predicate})(e)`);
          } catch {
            return false;
          }
        });
      },
      { eventType, predicate: predicate?.toString() },
      { timeout }
    );
  }
  
  async getNotifications() {
    return await this.page.locator('.notification').all();
  }
  
  async getNotificationCount(): Promise<number> {
    return await this.page.locator('.notification').count();
  }
  
  async dismissNotification(index: number = 0): Promise<void> {
    const notifications = await this.getNotifications();
    if (notifications[index]) {
      await notifications[index].locator('.notif-close').click();
    }
  }
  
  async getPresenceIndicator() {
    return this.page.locator('[data-testid="presence-indicator"]');
  }
  
  async getActiveUserCount(): Promise<number> {
    const state = await this.getCollaborationState();
    return state.activeUsers.length;
  }
  
  async hoverNode(nodeId: string): Promise<void> {
    const node = this.page.locator(`[data-node-id="${nodeId}"]`).first();
    await node.hover();
  }
}

/**
 * Multi-context test helper for testing collaboration between multiple users
 */
export class MultiUserCollaboration {
  private contexts: BrowserContext[] = [];
  private pages: CollaborationPage[] = [];
  
  async addUser(context: BrowserContext, userId: string, runId: string): Promise<CollaborationPage> {
    const page = await context.newPage();
    const collabPage = new CollaborationPage(page);
    
    await collabPage.setupUser(userId, runId);
    await collabPage.navigateToRun(runId);
    await collabPage.waitForConnection();
    
    this.contexts.push(context);
    this.pages.push(collabPage);
    
    return collabPage;
  }
  
  getPage(index: number): CollaborationPage {
    return this.pages[index];
  }
  
  getAllPages(): CollaborationPage[] {
    return this.pages;
  }
  
  async cleanup(): Promise<void> {
    for (const context of this.contexts) {
      await context.close();
    }
    this.contexts = [];
    this.pages = [];
  }
  
  async waitForAllConnected(timeout: number = 10000): Promise<void> {
    await Promise.all(this.pages.map((page) => page.waitForConnection(timeout)));
  }
  
  async waitForSync(delayMs: number = 2000): Promise<void> {
    await this.pages[0].page.waitForTimeout(delayMs);
  }
}

/**
 * WebSocket mock utilities for isolated testing
 */
export class WebSocketMock {
  static async install(page: Page): Promise<void> {
    await page.addInitScript(() => {
      (window as any).__originalWebSocket = (window as any).WebSocket;
      (window as any).__mockWebSocketInstances = [];
      (window as any).__sentMessages = [];
      
      class MockWebSocket extends EventTarget {
        readyState = 0;
        url: string;
        protocol: string;
        
        constructor(url: string, protocol?: string | string[]) {
          super();
          this.url = url;
          this.protocol = typeof protocol === 'string' ? protocol : '';
          (window as any).__mockWebSocketInstances.push(this);
          
          setTimeout(() => {
            this.readyState = 1;
            this.dispatchEvent(new Event('open'));
          }, 10);
        }
        
        send(data: string) {
          (window as any).__sentMessages.push({
            data,
            timestamp: Date.now(),
          });
        }
        
        close() {
          this.readyState = 3;
          this.dispatchEvent(new CloseEvent('close'));
        }
        
        simulateMessage(data: any) {
          const event = new MessageEvent('message', { data: JSON.stringify(data) });
          this.dispatchEvent(event);
        }
      }
      
      (window as any).WebSocket = MockWebSocket;
    });
  }
  
  static async simulateServerMessage(page: Page, message: any): Promise<void> {
    await page.evaluate((msg) => {
      const instances = (window as any).__mockWebSocketInstances;
      if (instances && instances.length > 0) {
        const ws = instances[instances.length - 1];
        ws.simulateMessage(msg);
      }
    }, message);
  }
  
  static async getSentMessages(page: Page): Promise<any[]> {
    return await page.evaluate(() => {
      return (window as any).__sentMessages || [];
    });
  }
  
  static async clearSentMessages(page: Page): Promise<void> {
    await page.evaluate(() => {
      (window as any).__sentMessages = [];
    });
  }
  
  static async uninstall(page: Page): Promise<void> {
    await page.evaluate(() => {
      if ((window as any).__originalWebSocket) {
        (window as any).WebSocket = (window as any).__originalWebSocket;
      }
    });
  }
}

/**
 * Assertions helper for collaboration events
 */
export class CollaborationAssertions {
  static hasEvent(events: any[], eventType: string, predicate?: (event: any) => boolean): boolean {
    const filtered = events.filter((e) => e.eventType === eventType);
    if (!predicate) {
      return filtered.length > 0;
    }
    return filtered.some(predicate);
  }
  
  static countEvents(events: any[], eventType: string): number {
    return events.filter((e) => e.eventType === eventType).length;
  }
  
  static findEvent(events: any[], eventType: string, predicate?: (event: any) => boolean): any {
    const filtered = events.filter((e) => e.eventType === eventType);
    if (!predicate) {
      return filtered[0];
    }
    return filtered.find(predicate);
  }
  
  static hasCursor(cursorPositions: [string, string][], userId: string): boolean {
    return cursorPositions.some(([id]) => id === userId);
  }
  
  static getCursorPosition(cursorPositions: [string, string][], userId: string): string | null {
    const cursor = cursorPositions.find(([id]) => id === userId);
    return cursor ? cursor[1] : null;
  }
  
  static hasActiveUser(activeUsers: string[], userId: string): boolean {
    return activeUsers.includes(userId);
  }
}

/**
 * Performance testing utilities
 */
export class PerformanceHelpers {
  static async measureEventPropagationTime(
    sender: CollaborationPage,
    receiver: CollaborationPage,
    action: () => Promise<void>
  ): Promise<number> {
    const initialCount = (await receiver.getCollaborationState()).events.length;
    const startTime = Date.now();
    
    await action();
    
    await receiver.page.waitForFunction(
      (expected) => {
        const store = (window as any).workflowStreamStore;
        return store.collaborationEvents().length > expected;
      },
      initialCount
    );
    
    return Date.now() - startTime;
  }
  
  static async stressTest(
    page: CollaborationPage,
    operations: Array<() => Promise<void>>,
    delayBetweenOps: number = 0
  ): Promise<void> {
    for (const op of operations) {
      await op();
      if (delayBetweenOps > 0) {
        await page.page.waitForTimeout(delayBetweenOps);
      }
    }
  }
}
