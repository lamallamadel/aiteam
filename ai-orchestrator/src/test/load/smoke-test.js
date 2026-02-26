// ─────────────────────────────────────────────────────────────
// K6 Smoke Test - Quick Health Check
// 
// Minimal smoke test to verify basic functionality before
// running full load tests. Runs with 1-5 users for 1 minute.
//
// Usage: k6 run smoke-test.js
// ─────────────────────────────────────────────────────────────

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'test-token-123';

export const options = {
  vus: 1,
  duration: '1m',
  thresholds: {
    'http_req_duration': ['p(95)<1000'],
    'errors': ['rate<0.05'],
  },
};

const params = {
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${AUTH_TOKEN}`,
  },
};

export default function() {
  // 1. Health check
  const healthRes = http.get(`${BASE_URL}/actuator/health`);
  errorRate.add(!check(healthRes, {
    'health check is 200': (r) => r.status === 200,
  }));
  
  sleep(1);
  
  // 2. List runs
  const listRes = http.get(`${BASE_URL}/api/runs?page=0&size=5`, params);
  errorRate.add(!check(listRes, {
    'list runs is 200': (r) => r.status === 200,
  }));
  
  sleep(1);
  
  // 3. Create run (optional - may fail if auth not configured)
  const createPayload = JSON.stringify({
    repoUrl: 'https://github.com/atlasia/backend',
    branch: 'main',
    agentType: 'security',
    prompt: 'Smoke test',
  });
  
  const createRes = http.post(`${BASE_URL}/api/runs`, createPayload, params);
  check(createRes, {
    'create run is 201 or 401': (r) => r.status === 201 || r.status === 401 || r.status === 403,
  });
  
  sleep(2);
}

export function setup() {
  console.log('🔥 Running smoke test...');
  console.log(`   Target: ${BASE_URL}`);
  
  const healthCheck = http.get(`${BASE_URL}/actuator/health`);
  if (healthCheck.status !== 200) {
    throw new Error(`API not healthy: ${healthCheck.status}`);
  }
  
  return { startTime: new Date().toISOString() };
}

export function teardown(data) {
  console.log('✅ Smoke test completed');
}
