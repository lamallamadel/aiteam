# Atlasia E2E Test Suite

## Overview

This directory contains end-to-end tests for the Atlasia AI Orchestrator frontend, with a comprehensive focus on multi-user collaboration features.

## Test Structure

### Core Test Files

- **`collaboration.spec.ts`** - Main collaboration feature tests
  - WebSocket connection/disconnection flows
  - Real-time graft/prune/flag mutations sync
  - Presence indicators and cursor position updates
  - Operational transformation conflict resolution
  - Collaboration notifications
  - SSE + WebSocket integration

- **`collaboration-advanced.spec.ts`** - Advanced scenarios
  - Circuit breaker patterns
  - WebSocket message reliability
  - State synchronization after network partitions
  - Performance metrics and stress testing
  - Security validations
  - Multi-run isolation

- **`collaboration-helpers.ts`** - Reusable test utilities
  - `CollaborationPage` - Page object for collaboration features
  - `MultiUserCollaboration` - Multi-context test helper
  - `WebSocketMock` - Mock WebSocket for isolated testing
  - `TestData` - Test data generators
  - `CollaborationAssertions` - Custom assertions
  - `PerformanceHelpers` - Performance testing utilities

- **`app.spec.ts`** - Basic application tests

## Test Coverage

### 1. WebSocket Connection Management
- ✅ Establish connection on page load
- ✅ Disconnect when leaving page
- ✅ Auto-reconnect after connection loss
- ✅ Send USER_JOIN/USER_LEAVE events
- ✅ Handle connection failures gracefully

### 2. Real-time Mutations
- ✅ Graft mutations sync across contexts
- ✅ Prune mutations sync across contexts
- ✅ Flag mutations sync across contexts
- ✅ Concurrent graft conflicts (Last-Write-Wins)
- ✅ Concurrent prune operations (CRDT)
- ✅ Concurrent prune/unprune of same step

### 3. Presence Indicators
- ✅ Show active users count
- ✅ Update when users join
- ✅ Update when users leave
- ✅ Display presence indicators in UI

### 4. Cursor Positions
- ✅ Sync cursor positions across contexts
- ✅ Update on node hover
- ✅ Show multiple cursors on same node
- ✅ Remove cursor when user leaves

### 5. Notifications
- ✅ Display notification for graft/prune/flag
- ✅ Display notification for join/leave
- ✅ Auto-dismiss after timeout
- ✅ Manual dismiss
- ✅ Don't show for own actions
- ✅ Limit notifications to prevent overflow

### 6. Operational Transformation
- ✅ Resolve concurrent graft conflicts
- ✅ Handle concurrent prune operations
- ✅ Handle rapid successive mutations
- ✅ Maintain event ordering

### 7. Circuit Breaker & Error Handling
- ✅ Handle disconnection gracefully
- ✅ Attempt reconnection with backoff
- ✅ Queue messages during disconnection
- ✅ Handle malformed messages

### 8. SSE + WebSocket Integration
- ✅ Maintain SSE during WebSocket activity
- ✅ Handle concurrent SSE and WebSocket messages
- ✅ Don't interfere when WebSocket disconnects
- ✅ Sync state across reconnections

### 9. Performance & Scale
- ✅ Handle multiple rapid cursor updates
- ✅ Handle large number of events
- ✅ Limit notifications UI overflow
- ✅ Measure event propagation latency
- ✅ Maintain acceptable memory usage

### 10. Edge Cases
- ✅ Same user in multiple tabs
- ✅ Empty or null mutation data
- ✅ Very long mutation data
- ✅ Rapid connect/disconnect cycles

## Running Tests

### Run all E2E tests
```bash
npm run e2e
```

### Run specific test file
```bash
npx playwright test e2e/collaboration.spec.ts
```

### Run with UI mode (debugging)
```bash
npx playwright test --ui
```

### Run specific browser
```bash
npx playwright test --project=chromium
npx playwright test --project=firefox
npx playwright test --project=webkit
```

### Run in headed mode
```bash
npx playwright test --headed
```

### Run with debug mode
```bash
npx playwright test --debug
```

## Test Configuration

Tests are configured via `playwright.fast.config.ts`:

- **Base URL**: `http://127.0.0.1:4200`
- **Timeout**: 60 seconds
- **Retries**: 2 (in CI), 0 (locally)
- **Workers**: 1 (in CI), unlimited (locally)
- **Browsers**: Chromium only (CI), all browsers (local)

## Prerequisites

### Backend Services
The collaboration tests require the backend services to be running:

```bash
# Start infrastructure
docker compose -f infra/docker-compose.ai.yml up -d

# Start backend
cd ai-orchestrator && mvn spring-boot:run
```

### Frontend Dev Server
The Playwright config automatically starts the dev server, but you can also start it manually:

```bash
cd frontend && npm run start
```

## Writing New Tests

### Using Page Objects

```typescript
import { test, expect } from '@playwright/test';
import { CollaborationPage, TestData } from './collaboration-helpers';

test('my collaboration test', async ({ page }) => {
  const collabPage = new CollaborationPage(page);
  
  await collabPage.setupUser(TestData.userIds.user1);
  await collabPage.navigateToRun(TestData.runIds.default);
  await collabPage.waitForConnection();
  
  await collabPage.sendGraft('ARCHITECT', 'my-agent');
  
  const state = await collabPage.getCollaborationState();
  expect(state.events).toHaveLength(1);
});
```

### Multi-User Tests

```typescript
import { test, expect } from '@playwright/test';
import { MultiUserCollaboration, TestData } from './collaboration-helpers';

test('multi-user scenario', async ({ browser }) => {
  const collab = new MultiUserCollaboration();
  
  const context1 = await browser.newContext();
  const context2 = await browser.newContext();
  
  const page1 = await collab.addUser(
    context1,
    TestData.userIds.user1,
    TestData.runIds.default
  );
  
  const page2 = await collab.addUser(
    context2,
    TestData.userIds.user2,
    TestData.runIds.default
  );
  
  await page1.sendGraft('ARCHITECT', 'agent-1');
  await collab.waitForSync();
  
  const state2 = await page2.getCollaborationState();
  expect(state2.events).toContainEqual(
    expect.objectContaining({ eventType: 'GRAFT' })
  );
  
  await collab.cleanup();
});
```

### Using WebSocket Mocks

```typescript
import { test, expect } from '@playwright/test';
import { WebSocketMock, MockWebSocketResponses, TestData } from './collaboration-helpers';

test('isolated WebSocket test', async ({ page }) => {
  await WebSocketMock.install(page);
  
  // Navigate and set up
  await page.goto(`/runs/${TestData.runIds.default}`);
  
  // Simulate server message
  const event = MockWebSocketResponses.graftEvent(
    TestData.userIds.user2,
    'ARCHITECT',
    'test-agent'
  );
  
  await WebSocketMock.simulateServerMessage(page, event);
  
  // Verify event received
  const state = await page.evaluate(() => {
    const store = (window as any).workflowStreamStore;
    return store.collaborationEvents();
  });
  
  expect(state).toHaveLength(1);
  
  await WebSocketMock.uninstall(page);
});
```

## Troubleshooting

### Tests timing out
- Increase timeout in test or config
- Check if backend services are running
- Check network connectivity

### WebSocket connection failures
- Verify backend WebSocket endpoint is accessible
- Check for CORS issues
- Ensure SockJS is properly configured

### Flaky tests
- Add explicit waits for async operations
- Use `waitForFunction` instead of fixed timeouts
- Check for race conditions in multi-user tests

### Test isolation issues
- Ensure proper cleanup in `afterEach` hooks
- Clear localStorage between tests
- Close all browser contexts

## CI/CD Integration

Tests run automatically in CI pipeline:

```yaml
- name: E2E Tests
  run: |
    docker compose -f infra/docker-compose.ai.yml up -d
    cd frontend && npm ci
    npm run e2e
```

## Performance Benchmarks

Target performance metrics:

- Event propagation latency: < 2 seconds
- Reconnection time: < 5 seconds
- Memory usage: < 100MB for 500 events
- Notification render: < 100ms

## Contributing

When adding new collaboration features:

1. Add test coverage in `collaboration.spec.ts`
2. Add edge cases in `collaboration-advanced.spec.ts`
3. Update helpers in `collaboration-helpers.ts` if needed
4. Update this README with new test coverage
5. Ensure all tests pass locally before committing

## Related Documentation

- [COLLABORATION.md](../../docs/COLLABORATION.md) - Feature documentation
- [COLLABORATION_EXAMPLES.md](../../docs/COLLABORATION_EXAMPLES.md) - Usage examples
- [Playwright Documentation](https://playwright.dev/)
