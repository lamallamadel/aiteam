// ─────────────────────────────────────────────────────────────
// K6 Load Test - Shared Configuration
// 
// Common configuration, helpers, and utilities for all load tests.
// ─────────────────────────────────────────────────────────────

// Environment configuration
export const config = {
  baseUrl: __ENV.BASE_URL || 'http://localhost:8088',
  wsUrl: __ENV.WS_URL || 'ws://localhost:8088',
  authToken: __ENV.AUTH_TOKEN || 'test-token-123',
  a2aToken: __ENV.A2A_TOKEN || 'a2a-secret-token',
};

// Standard HTTP headers
export function getHeaders(additionalHeaders = {}) {
  return Object.assign(
    {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${config.authToken}`,
      'User-Agent': 'k6-load-test',
    },
    additionalHeaders
  );
}

// Standard A2A headers
export function getA2AHeaders() {
  return getHeaders({
    'X-A2A-Token': config.a2aToken,
    'X-Agent-Id': `agent-${__VU}`,
  });
}

// Performance baseline constants
export const baselines = {
  api: {
    p95Latency: 500,      // 500ms
    p99Latency: 1000,     // 1s
    errorRate: 0.01,      // 1%
    throughput: 100,      // 100 RPS
  },
  ai: {
    p95Latency: 2000,     // 2s
    p99Latency: 5000,     // 5s
    successRate: 0.99,    // 99%
  },
  a2a: {
    p95Latency: 500,      // 500ms
    executionP95: 2000,   // 2s
    completionRate: 0.95, // 95%
    throughput: 50,       // 50 TPS
  },
  websocket: {
    connectionP95: 1000,  // 1s
    messageP95: 100,      // 100ms
    stability: 0.99,      // 99%
    reconnectionP95: 3000,// 3s
  },
};

// Common load test profiles
export const loadProfiles = {
  smoke: [
    { duration: '1m', target: 5 },
  ],
  load: [
    { duration: '2m', target: 20 },
    { duration: '5m', target: 50 },
    { duration: '2m', target: 20 },
    { duration: '1m', target: 0 },
  ],
  stress: [
    { duration: '2m', target: 50 },
    { duration: '5m', target: 100 },
    { duration: '5m', target: 150 },
    { duration: '2m', target: 50 },
    { duration: '1m', target: 0 },
  ],
  spike: [
    { duration: '1m', target: 20 },
    { duration: '30s', target: 200 },
    { duration: '1m', target: 20 },
    { duration: '30s', target: 0 },
  ],
  soak: [
    { duration: '5m', target: 50 },
    { duration: '30m', target: 50 },
    { duration: '5m', target: 0 },
  ],
};

// Random data generators
export function randomString(length = 10) {
  const chars = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let result = '';
  for (let i = 0; i < length; i++) {
    result += chars.charAt(Math.floor(Math.random() * chars.length));
  }
  return result;
}

export function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min;
}

export function randomChoice(array) {
  return array[Math.floor(Math.random() * array.length)];
}

export function randomBoolean() {
  return Math.random() >= 0.5;
}

// Think time helpers
export function thinkTime(min = 1, max = 3) {
  return Math.random() * (max - min) + min;
}

// Test data pools
export const testData = {
  repositories: [
    'https://github.com/atlasia/backend',
    'https://github.com/atlasia/frontend',
    'https://github.com/atlasia/infra',
    'https://github.com/atlasia/docs',
    'https://github.com/atlasia/ai-agents',
  ],
  branches: ['main', 'develop', 'feature/new-feature', 'hotfix/critical'],
  agentTypes: ['security', 'quality', 'sre', 'frontend'],
  taskTypes: [
    { type: 'security_scan', complexity: 'high', duration: 3000 },
    { type: 'code_review', complexity: 'medium', duration: 1500 },
    { type: 'linting', complexity: 'low', duration: 500 },
    { type: 'test_generation', complexity: 'medium', duration: 2000 },
    { type: 'documentation', complexity: 'low', duration: 800 },
  ],
  severityLevels: ['low', 'medium', 'high', 'critical'],
  statuses: ['pending', 'running', 'completed', 'failed', 'cancelled'],
};

// Logging helpers
export function logTestStart(testName, target) {
  console.log(`🚀 Starting ${testName}`);
  console.log(`   Target: ${target}`);
  console.log(`   Base URL: ${config.baseUrl}`);
}

export function logTestEnd(testName, duration) {
  console.log(`✅ Completed ${testName}`);
  console.log(`   Duration: ${duration}`);
}

// Validation helpers
export function validateResponse(response, expectedStatus, checks = {}) {
  const validations = {
    [`status is ${expectedStatus}`]: (r) => r.status === expectedStatus,
  };
  
  Object.keys(checks).forEach(key => {
    validations[key] = checks[key];
  });
  
  return validations;
}

export function validateJsonResponse(response, schema = {}) {
  try {
    const body = JSON.parse(response.body);
    
    // Validate required fields
    const requiredFields = Object.keys(schema);
    return requiredFields.every(field => {
      if (schema[field] === 'required') {
        return body[field] !== undefined;
      }
      return true;
    });
  } catch (e) {
    return false;
  }
}

// Metric helpers
export function recordMetric(metricName, value, tags = {}) {
  // This is a placeholder - actual implementation depends on k6 version
  console.log(`Metric: ${metricName} = ${value}`, tags);
}

// Error handling
export function handleError(error, context = '') {
  console.error(`❌ Error${context ? ` (${context})` : ''}: ${error.message || error}`);
}

// Health check helper
export function checkHealth(http) {
  const healthUrl = `${config.baseUrl}/actuator/health`;
  try {
    const response = http.get(healthUrl);
    return response.status === 200;
  } catch (e) {
    return false;
  }
}
