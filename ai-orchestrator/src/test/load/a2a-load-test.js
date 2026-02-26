// ─────────────────────────────────────────────────────────────
// K6 Load Test - A2A Protocol (/api/a2a/tasks)
// 
// Simulates agent-to-agent communication patterns with high
// concurrency and complex task workflows.
//
// Performance Baselines:
//   - p95 latency: < 500ms for task submission
//   - p95 latency: < 2s for task execution with AI
//   - Error rate: < 1%
//   - Throughput: > 50 TPS (tasks per second)
// ─────────────────────────────────────────────────────────────

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// Custom metrics
const errorRate = new Rate('a2a_errors');
const taskSubmissionLatency = new Trend('task_submission_latency');
const taskExecutionLatency = new Trend('task_execution_latency');
const taskCompletionRate = new Rate('task_completion_rate');
const tasksSubmitted = new Counter('tasks_submitted');
const tasksCompleted = new Counter('tasks_completed');

// Configuration
const BASE_URL = __ENV.BASE_URL || 'http://localhost:8088';
const A2A_TOKEN = __ENV.A2A_TOKEN || 'a2a-secret-token';

// Load test stages
export const options = {
  stages: [
    { duration: '30s', target: 10 },  // Warm up
    { duration: '2m', target: 30 },   // Ramp up to 30 agents
    { duration: '3m', target: 60 },   // Ramp up to 60 agents
    { duration: '5m', target: 100 },  // Ramp up to 100 agents
    { duration: '5m', target: 100 },  // Sustain 100 agents
    { duration: '2m', target: 120 },  // Spike to 120 agents
    { duration: '1m', target: 50 },   // Ramp down
    { duration: '30s', target: 0 },   // Cool down
  ],
  thresholds: {
    'http_req_duration': ['p(95)<500'],
    'http_req_duration{operation:submit}': ['p(95)<500'],
    'http_req_duration{operation:execute}': ['p(95)<2000'],
    'a2a_errors': ['rate<0.01'],
    'http_req_failed': ['rate<0.01'],
    'task_completion_rate': ['rate>0.95'],
  },
};

// Task types and complexities
const taskTypes = [
  { type: 'security_scan', complexity: 'high', avgDuration: 3000 },
  { type: 'code_review', complexity: 'medium', avgDuration: 1500 },
  { type: 'linting', complexity: 'low', avgDuration: 500 },
  { type: 'test_generation', complexity: 'medium', avgDuration: 2000 },
  { type: 'documentation', complexity: 'low', avgDuration: 800 },
];

// Generate A2A task payload
function generateA2ATask() {
  const task = taskTypes[Math.floor(Math.random() * taskTypes.length)];
  
  return {
    sourceAgentId: `agent-${__VU}`,
    targetAgentId: `agent-${Math.floor(Math.random() * 100)}`,
    taskType: task.type,
    priority: Math.random() > 0.8 ? 'high' : 'normal',
    payload: {
      repositoryUrl: 'https://github.com/atlasia/backend',
      branch: 'main',
      files: generateFileList(),
      context: {
        triggeredBy: `workflow-${__ITER}`,
        complexity: task.complexity,
        estimatedDuration: task.avgDuration,
      }
    },
    metadata: {
      timestamp: new Date().toISOString(),
      correlationId: `corr-${__VU}-${__ITER}`,
      traceId: `trace-${Date.now()}-${Math.random()}`,
    }
  };
}

function generateFileList() {
  const fileCount = Math.floor(Math.random() * 10) + 1;
  const files = [];
  
  for (let i = 0; i < fileCount; i++) {
    files.push(`src/main/java/com/atlasia/service/Service${i}.java`);
  }
  
  return files;
}

// HTTP parameters
const params = {
  headers: {
    'Content-Type': 'application/json',
    'X-A2A-Token': A2A_TOKEN,
    'X-Agent-Id': `agent-${__VU}`,
  },
};

// Main test scenario
export default function() {
  // 1. Submit a new A2A task (Primary flow - 70% of operations)
  if (Math.random() < 0.7) {
    const taskPayload = JSON.stringify(generateA2ATask());
    
    const submitRes = http.post(
      `${BASE_URL}/api/a2a/tasks`,
      taskPayload,
      Object.assign({}, params, { tags: { operation: 'submit' } })
    );
    
    const submitSuccess = check(submitRes, {
      'task submitted successfully': (r) => r.status === 201 || r.status === 202,
      'task has id': (r) => {
        try {
          return JSON.parse(r.body).taskId !== undefined;
        } catch {
          return false;
        }
      },
    });
    
    errorRate.add(!submitSuccess);
    taskSubmissionLatency.add(submitRes.timings.duration);
    
    if (submitSuccess) {
      tasksSubmitted.add(1);
      
      const taskId = JSON.parse(submitRes.body).taskId;
      
      // Simulate async processing delay
      sleep(0.5);
      
      // 2. Poll for task status
      const statusRes = http.get(
        `${BASE_URL}/api/a2a/tasks/${taskId}`,
        Object.assign({}, params, { tags: { operation: 'status' } })
      );
      
      check(statusRes, {
        'status check successful': (r) => r.status === 200,
        'task has status field': (r) => {
          try {
            return JSON.parse(r.body).status !== undefined;
          } catch {
            return false;
          }
        },
      });
      
      // 3. Check if task completed
      if (statusRes.status === 200) {
        const taskStatus = JSON.parse(statusRes.body).status;
        if (taskStatus === 'completed' || taskStatus === 'failed') {
          tasksCompleted.add(1);
          taskCompletionRate.add(taskStatus === 'completed' ? 1 : 0);
        }
      }
    }
  }
  
  // 4. Query task queue (20% of operations)
  if (Math.random() < 0.2) {
    const queueRes = http.get(
      `${BASE_URL}/api/a2a/tasks/queue?agentId=agent-${__VU}&status=pending`,
      Object.assign({}, params, { tags: { operation: 'queue' } })
    );
    
    check(queueRes, {
      'queue query successful': (r) => r.status === 200,
      'queue has tasks array': (r) => {
        try {
          return Array.isArray(JSON.parse(r.body).tasks);
        } catch {
          return false;
        }
      },
    });
  }
  
  // 5. Execute task (simulate agent processing) - 30% of operations
  if (Math.random() < 0.3) {
    const taskId = `task-${Math.floor(Math.random() * 1000)}`;
    const executionPayload = JSON.stringify({
      taskId: taskId,
      agentId: `agent-${__VU}`,
      result: {
        status: 'completed',
        findings: generateFindings(),
        executionTime: Math.floor(Math.random() * 2000) + 500,
      }
    });
    
    const executeRes = http.put(
      `${BASE_URL}/api/a2a/tasks/${taskId}/execute`,
      executionPayload,
      Object.assign({}, params, { tags: { operation: 'execute' } })
    );
    
    const executeSuccess = check(executeRes, {
      'task execution accepted': (r) => r.status === 200 || r.status === 404,
    });
    
    if (executeSuccess && executeRes.status === 200) {
      taskExecutionLatency.add(executeRes.timings.duration);
      tasksCompleted.add(1);
      taskCompletionRate.add(1);
    }
  }
  
  // 6. Cancel task (5% of operations)
  if (Math.random() < 0.05) {
    const taskId = `task-${Math.floor(Math.random() * 100)}`;
    const cancelRes = http.del(
      `${BASE_URL}/api/a2a/tasks/${taskId}`,
      null,
      Object.assign({}, params, { tags: { operation: 'cancel' } })
    );
    
    check(cancelRes, {
      'task cancelled or not found': (r) => r.status === 204 || r.status === 404,
    });
  }
  
  // Think time between operations (agents processing)
  sleep(Math.random() * 1.5 + 0.5); // 0.5-2 seconds
}

function generateFindings() {
  const findingCount = Math.floor(Math.random() * 5);
  const findings = [];
  
  for (let i = 0; i < findingCount; i++) {
    findings.push({
      severity: ['low', 'medium', 'high'][Math.floor(Math.random() * 3)],
      message: `Finding ${i + 1}`,
      location: `line ${Math.floor(Math.random() * 100)}`,
    });
  }
  
  return findings;
}

// Setup function
export function setup() {
  console.log(`🚀 Starting A2A protocol load test against ${BASE_URL}`);
  console.log(`🎯 Target: 100+ concurrent agents`);
  console.log(`📊 Performance baselines:`);
  console.log(`   - Task submission p95: < 500ms`);
  console.log(`   - Task execution p95: < 2s`);
  console.log(`   - Completion rate: > 95%`);
  
  // Verify A2A endpoint is available
  const healthCheck = http.get(`${BASE_URL}/actuator/health`);
  if (healthCheck.status !== 200) {
    throw new Error(`API health check failed: ${healthCheck.status}`);
  }
  
  return { startTime: new Date().toISOString() };
}

// Teardown function
export function teardown(data) {
  console.log(`✅ A2A load test completed`);
  console.log(`   Started at: ${data.startTime}`);
  console.log(`   Completed at: ${new Date().toISOString()}`);
}
