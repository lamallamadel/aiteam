import { Page, BrowserContext } from '@playwright/test';

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
  
  async setupUser(userId: string): Promise<void> {
    await this.page.evaluate((id) => {
      localStorage.setItem('atlasia_user_id', id);
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
      return {
        isConnected: store?.isCollaborationConnected() || false,
        activeUsers: store?.activeUsers() || [],
        events: store?.collaborationEvents() || [],
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
    
    await collabPage.setupUser(userId);
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
