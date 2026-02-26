// ─────────────────────────────────────────────────────────────
// K6 Load Test - API Endpoints (/api/runs)
// 
// Simulates 100+ concurrent users performing CRUD operations
// on workflow runs with realistic workload patterns.
//
// Performance Baselines:
//   - p95 latency: < 500ms for API calls
//   - p95 latency: < 2s for AI agent responses
//   - Error rate: < 1%
//   - Throughput: > 100 RPS
// ─────────────────────────────────────────────────────────────

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('errors');
const apiLatency = new Trend('api_latency');
const aiLatency = new Trend('ai_latency');
const successfulRequests = new Counter('successful_requests');

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';
const AUTH_TOKEN = __ENV.AUTH_TOKEN || 'test-token-123';

// Load test stages
export const options = {
  stages: [
    { duration: '1m', target: 20 },   // Ramp up to 20 users
    { duration: '3m', target: 50 },   // Ramp up to 50 users
    { duration: '5m', target: 100 },  // Ramp up to 100 users
    { duration: '5m', target: 100 },  // Stay at 100 users
    { duration: '3m', target: 150 },  // Spike to 150 users
    { duration: '2m', target: 50 },   // Ramp down to 50 users
    { duration: '1m', target: 0 },    // Ramp down to 0 users
  ],
  thresholds: {
    'http_req_duration': ['p(95)<500'], // 95% of requests must complete below 500ms
    'http_req_duration{type:ai}': ['p(95)<2000'], // AI responses < 2s
    'errors': ['rate<0.01'], // Error rate < 1%
    'http_req_failed': ['rate<0.01'],
    'successful_requests': ['count>1000'], // At least 1000 successful requests
  },
  ext: {
    loadimpact: {
      projectID: 3649635,
      name: 'API Load Test - Atlasia'
    }
  }
};

// Test data generator
function generateRunPayload() {
  const repositories = [
    'https://github.com/atlasia/backend',
    'https://github.com/atlasia/frontend',
    'https://github.com/atlasia/infra'
  ];
  
  const agentTypes = ['security', 'quality', 'sre', 'frontend'];
  
  return {
    repoUrl: repositories[Math.floor(Math.random() * repositories.length)],
    branch: 'main',
    agentType: agentTypes[Math.floor(Math.random() * agentTypes.length)],
    prompt: `Analyze code for ${Math.random().toString(36).substring(7)}`,
    metadata: {
      userId: `user-${__VU}`,
      sessionId: `session-${__ITER}`,
      timestamp: new Date().toISOString()
    }
  };
}

// HTTP parameters
const params = {
  headers: {
    'Content-Type': 'application/json',
    'Authorization': `Bearer ${AUTH_TOKEN}`,
  },
  tags: { type: 'api' }
};

// Main test scenario
export default function() {
  const iterationStartTime = new Date();
  
  // 1. Create a new workflow run (30% of requests)
  if (Math.random() < 0.3) {
    const createPayload = JSON.stringify(generateRunPayload());
    const createRes = http.post(
      `${BASE_URL}/api/runs`,
      createPayload,
      params
    );
    
    const createSuccess = check(createRes, {
      'create run status is 201': (r) => r.status === 201,
      'create run has id': (r) => JSON.parse(r.body).id !== undefined,
    });
    
    errorRate.add(!createSuccess);
    apiLatency.add(createRes.timings.duration);
    
    if (createSuccess) {
      successfulRequests.add(1);
      const runId = JSON.parse(createRes.body).id;
      
      // Wait for AI processing
      sleep(1);
      
      // Poll for completion
      const statusRes = http.get(
        `${BASE_URL}/api/runs/${runId}`,
        Object.assign({}, params, { tags: { type: 'ai' } })
      );
      
      const statusSuccess = check(statusRes, {
        'status check is 200': (r) => r.status === 200,
        'run has status': (r) => JSON.parse(r.body).status !== undefined,
      });
      
      errorRate.add(!statusSuccess);
      aiLatency.add(statusRes.timings.duration);
      
      if (statusSuccess) {
        successfulRequests.add(1);
      }
    }
  }
  
  // 2. List workflow runs (50% of requests)
  if (Math.random() < 0.5) {
    const listRes = http.get(
      `${BASE_URL}/api/runs?page=0&size=20&sort=createdAt,desc`,
      params
    );
    
    const listSuccess = check(listRes, {
      'list runs status is 200': (r) => r.status === 200,
      'list runs has content': (r) => JSON.parse(r.body).content !== undefined,
    });
    
    errorRate.add(!listSuccess);
    apiLatency.add(listRes.timings.duration);
    
    if (listSuccess) {
      successfulRequests.add(1);
    }
  }
  
  // 3. Get specific run details (60% of requests)
  if (Math.random() < 0.6) {
    // Use a random run ID (in real scenario, would use from list)
    const runId = Math.floor(Math.random() * 1000) + 1;
    const getRes = http.get(
      `${BASE_URL}/api/runs/${runId}`,
      params
    );
    
    const getSuccess = check(getRes, {
      'get run status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    });
    
    errorRate.add(!getSuccess);
    apiLatency.add(getRes.timings.duration);
    
    if (getSuccess && getRes.status === 200) {
      successfulRequests.add(1);
    }
  }
  
  // 4. Update run (grafting) - (20% of requests)
  if (Math.random() < 0.2) {
    const runId = Math.floor(Math.random() * 100) + 1;
    const graftPayload = JSON.stringify({
      agentId: 'security-agent',
      position: Math.floor(Math.random() * 5),
      reason: 'Additional security scan required'
    });
    
    const graftRes = http.post(
      `${BASE_URL}/api/runs/${runId}/graft`,
      graftPayload,
      params
    );
    
    const graftSuccess = check(graftRes, {
      'graft status is 200 or 404': (r) => r.status === 200 || r.status === 404,
    });
    
    errorRate.add(!graftSuccess);
    apiLatency.add(graftRes.timings.duration);
    
    if (graftSuccess && graftRes.status === 200) {
      successfulRequests.add(1);
    }
  }
  
  // 5. Delete run (10% of requests)
  if (Math.random() < 0.1) {
    const runId = Math.floor(Math.random() * 50) + 1;
    const deleteRes = http.del(
      `${BASE_URL}/api/runs/${runId}`,
      null,
      params
    );
    
    const deleteSuccess = check(deleteRes, {
      'delete status is 204 or 404': (r) => r.status === 204 || r.status === 404,
    });
    
    errorRate.add(!deleteSuccess);
    apiLatency.add(deleteRes.timings.duration);
    
    if (deleteSuccess) {
      successfulRequests.add(1);
    }
  }
  
  // Think time between requests (simulate user reading/thinking)
  sleep(Math.random() * 2 + 1); // 1-3 seconds
}

// Setup function (runs once per VU)
export function setup() {
  console.log(`🚀 Starting API load test against ${BASE_URL}`);
  console.log(`🎯 Target: 100+ concurrent users`);
  console.log(`📊 Performance baselines:`);
  console.log(`   - API p95 latency: < 500ms`);
  console.log(`   - AI p95 latency: < 2s`);
  console.log(`   - Error rate: < 1%`);
  
  // Verify API is available
  const healthCheck = http.get(`${BASE_URL}/actuator/health`);
  if (healthCheck.status !== 200) {
    throw new Error(`API health check failed: ${healthCheck.status}`);
  }
  
  return { startTime: new Date().toISOString() };
}

// Teardown function (runs once after all VUs complete)
export function teardown(data) {
  console.log(`✅ API load test completed`);
  console.log(`   Started at: ${data.startTime}`);
  console.log(`   Completed at: ${new Date().toISOString()}`);
}
