// ─────────────────────────────────────────────────────────────
// K6 Load Test - WebSocket Collaboration (/ws/runs/{runId}/collaboration)
// 
// Simulates real-time multi-user collaboration via WebSocket
// connections with CRDT-based synchronization.
//
// Performance Baselines:
//   - Connection establishment: < 1s
//   - Message latency: < 100ms
//   - Concurrent connections: > 50
//   - Connection stability: > 99% uptime
//   - Reconnection time: < 3s
// ─────────────────────────────────────────────────────────────

import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter, Gauge } from 'k6/metrics';

// Custom metrics
const wsConnectionTime = new Trend('ws_connection_time');
const wsMessageLatency = new Trend('ws_message_latency');
const wsErrorRate = new Rate('ws_errors');
const activeConnections = new Gauge('active_ws_connections');
const messagesReceived = new Counter('ws_messages_received');
const messagesSent = new Counter('ws_messages_sent');
const reconnections = new Counter('ws_reconnections');

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';
const WS_URL = __ENV.WS_URL || 'ws://localhost:8088';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'test-token-123';

// Load test stages - WebSocket focused
export const options = {
  stages: [
    { duration: '30s', target: 10 },  // Initial connections
    { duration: '1m', target: 25 },   // Ramp up to 25 connections
    { duration: '2m', target: 50 },   // Ramp up to 50 connections
    { duration: '5m', target: 75 },   // Ramp up to 75 connections
    { duration: '5m', target: 75 },   // Sustain 75 connections
    { duration: '2m', target: 100 },  // Spike to 100 connections
    { duration: '2m', target: 50 },   // Ramp down
    { duration: '1m', target: 0 },    // Close all connections
  ],
  thresholds: {
    'ws_connection_time': ['p(95)<1000'], // Connection < 1s
    'ws_message_latency': ['p(95)<100'],  // Message latency < 100ms
    'ws_errors': ['rate<0.01'],           // Error rate < 1%
    'active_ws_connections': ['value>50'], // Concurrent connections > 50
  },
};

// WebSocket message types
const messageTypes = {
  SUBSCRIBE: 'SUBSCRIBE',
  GRAFT: 'GRAFT',
  PRUNE: 'PRUNE',
  FLAG: 'FLAG',
  CURSOR_MOVE: 'CURSOR_MOVE',
  PRESENCE: 'PRESENCE',
  CRDT_UPDATE: 'CRDT_UPDATE',
};

// Generate test run IDs
function getRunId() {
  const runIds = [1, 2, 3, 4, 5, 10, 15, 20, 25, 30];
  return runIds[Math.floor(Math.random() * runIds.length)];
}

// Generate WebSocket messages
function generateGraftMessage(userId, runId) {
  return JSON.stringify({
    type: messageTypes.GRAFT,
    payload: {
      runId: runId,
      agentId: `agent-${Math.floor(Math.random() * 10)}`,
      position: Math.floor(Math.random() * 5),
      reason: 'Load test graft operation',
      userId: userId,
      timestamp: new Date().toISOString(),
    }
  });
}

function generatePruneMessage(userId, runId) {
  return JSON.stringify({
    type: messageTypes.PRUNE,
    payload: {
      runId: runId,
      agentId: `agent-${Math.floor(Math.random() * 10)}`,
      userId: userId,
      timestamp: new Date().toISOString(),
    }
  });
}

function generateFlagMessage(userId, runId) {
  return JSON.stringify({
    type: messageTypes.FLAG,
    payload: {
      runId: runId,
      agentId: `agent-${Math.floor(Math.random() * 10)}`,
      flag: Math.random() > 0.5 ? 'review' : 'approved',
      userId: userId,
      timestamp: new Date().toISOString(),
    }
  });
}

function generateCursorMessage(userId, runId) {
  return JSON.stringify({
    type: messageTypes.CURSOR_MOVE,
    payload: {
      runId: runId,
      userId: userId,
      position: {
        agentIndex: Math.floor(Math.random() * 10),
        x: Math.floor(Math.random() * 1000),
        y: Math.floor(Math.random() * 800),
      },
      timestamp: new Date().toISOString(),
    }
  });
}

function generatePresenceMessage(userId) {
  return JSON.stringify({
    type: messageTypes.PRESENCE,
    payload: {
      userId: userId,
      status: 'active',
      timestamp: new Date().toISOString(),
    }
  });
}

// Main test scenario
export default function() {
  const runId = getRunId();
  const userId = `user-${__VU}`;
  const wsUrl = `${WS_URL}/ws/runs/${runId}/collaboration`;
  
  const connectionStart = Date.now();
  let connectionEstablished = false;
  let messageCount = 0;
  let lastMessageTime = 0;
  
  const res = ws.connect(
    wsUrl,
    {
      headers: {
        'Authorization': `Bearer ${AUTH_TOKEN}`,
        'X-User-Id': userId,
      },
      tags: { protocol: 'websocket' },
    },
    function(socket) {
      connectionEstablished = true;
      const connectionTime = Date.now() - connectionStart;
      wsConnectionTime.add(connectionTime);
      activeConnections.add(1);
      
      // Subscribe to run
      const subscribeMsg = JSON.stringify({
        type: messageTypes.SUBSCRIBE,
        payload: {
          runId: runId,
          userId: userId,
        }
      });
      
      socket.send(subscribeMsg);
      messagesSent.add(1);
      
      // Handle incoming messages
      socket.on('message', function(data) {
        messageCount++;
        messagesReceived.add(1);
        
        // Calculate message latency
        try {
          const message = JSON.parse(data);
          if (message.timestamp) {
            const sentTime = new Date(message.timestamp).getTime();
            const latency = Date.now() - sentTime;
            wsMessageLatency.add(latency);
          }
        } catch (e) {
          // Ignore parse errors for latency calculation
        }
        
        lastMessageTime = Date.now();
      });
      
      socket.on('error', function(e) {
        wsErrorRate.add(1);
        console.log(`WebSocket error for user ${userId}: ${e.error()}`);
      });
      
      socket.on('close', function() {
        activeConnections.add(-1);
      });
      
      // Simulate user activity over time
      const sessionDuration = 30 + Math.random() * 60; // 30-90 seconds
      const operationInterval = 2 + Math.random() * 3; // 2-5 seconds between operations
      const operations = Math.floor(sessionDuration / operationInterval);
      
      for (let i = 0; i < operations; i++) {
        // Send presence heartbeat
        if (i % 5 === 0) {
          socket.send(generatePresenceMessage(userId));
          messagesSent.add(1);
        }
        
        // Randomly perform different operations
        const rand = Math.random();
        
        if (rand < 0.3) {
          // Graft operation (30%)
          socket.send(generateGraftMessage(userId, runId));
          messagesSent.add(1);
        } else if (rand < 0.5) {
          // Prune operation (20%)
          socket.send(generatePruneMessage(userId, runId));
          messagesSent.add(1);
        } else if (rand < 0.65) {
          // Flag operation (15%)
          socket.send(generateFlagMessage(userId, runId));
          messagesSent.add(1);
        } else {
          // Cursor movement (35%)
          socket.send(generateCursorMessage(userId, runId));
          messagesSent.add(1);
        }
        
        // Wait before next operation
        socket.setTimeout(function() {
          // Check connection health
          const timeSinceLastMessage = Date.now() - lastMessageTime;
          if (timeSinceLastMessage > 10000) {
            // No message in 10 seconds, potential connection issue
            wsErrorRate.add(1);
          }
        }, operationInterval * 1000);
        
        sleep(operationInterval);
      }
      
      // Graceful disconnect
      socket.close();
    }
  );
  
  // Check connection establishment
  check(res, {
    'WebSocket connected successfully': (r) => r && r.status === 101,
  });
  
  if (!connectionEstablished) {
    wsErrorRate.add(1);
    reconnections.add(1);
    
    // Simulate reconnection attempt
    sleep(2);
  }
  
  // Brief pause before next VU iteration
  sleep(1);
}

// Setup function
export function setup() {
  console.log(`🚀 Starting WebSocket load test against ${WS_URL}`);
  console.log(`🎯 Target: 50+ concurrent WebSocket connections`);
  console.log(`📊 Performance baselines:`);
  console.log(`   - Connection time p95: < 1s`);
  console.log(`   - Message latency p95: < 100ms`);
  console.log(`   - Connection stability: > 99%`);
  
  // Create test runs via REST API
  const testRuns = [1, 2, 3, 4, 5, 10, 15, 20, 25, 30];
  
  console.log(`📝 Using test run IDs: ${testRuns.join(', ')}`);
  
  return {
    startTime: new Date().toISOString(),
    testRuns: testRuns,
  };
}

// Teardown function
export function teardown(data) {
  console.log(`✅ WebSocket load test completed`);
  console.log(`   Started at: ${data.startTime}`);
  console.log(`   Completed at: ${new Date().toISOString()}`);
  console.log(`   Test runs used: ${data.testRuns.join(', ')}`);
}
