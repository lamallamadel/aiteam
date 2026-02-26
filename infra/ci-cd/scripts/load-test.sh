#!/bin/bash
# ─────────────────────────────────────────────────────────────
# K6 Load Testing Wrapper Script
# 
# Usage:
#   ./load-test.sh [test-scenario] [environment]
#
# Examples:
#   ./load-test.sh all local
#   ./load-test.sh api staging
#   ./load-test.sh websocket production
#
# Environment:
#   local      - http://localhost:8088
#   staging    - https://staging.example.com
#   production - https://api.example.com
# ─────────────────────────────────────────────────────────────

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
LOAD_TEST_DIR="$REPO_ROOT/ai-orchestrator/src/test/load"
RESULTS_DIR="$REPO_ROOT/ai-orchestrator/target/load-test-results"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Default values
TEST_SCENARIO="${1:-all}"
ENVIRONMENT="${2:-local}"
K6_DOCKER_IMAGE="grafana/k6:0.49.0"

# Environment URLs
case "$ENVIRONMENT" in
  local)
    BASE_URL="http://localhost:8088"
    WS_URL="ws://localhost:8088"
    ;;
  staging)
    BASE_URL="${STAGING_URL:-https://staging.example.com}"
    WS_URL="${STAGING_WS_URL:-wss://staging.example.com}"
    ;;
  production)
    BASE_URL="${PRODUCTION_URL:-https://api.example.com}"
    WS_URL="${PRODUCTION_WS_URL:-wss://api.example.com}"
    ;;
  *)
    echo -e "${RED}❌ Unknown environment: $ENVIRONMENT${NC}"
    echo "Valid environments: local, staging, production"
    exit 1
    ;;
esac

# Helper functions
log_info() {
  echo -e "${BLUE}ℹ️  $1${NC}"
}

log_success() {
  echo -e "${GREEN}✅ $1${NC}"
}

log_warning() {
  echo -e "${YELLOW}⚠️  $1${NC}"
}

log_error() {
  echo -e "${RED}❌ $1${NC}"
}

# Check if k6 is available
check_k6() {
  if command -v k6 &> /dev/null; then
    log_info "Using local k6: $(k6 version)"
    K6_CMD="k6"
    return 0
  elif command -v docker &> /dev/null; then
    log_info "Using k6 Docker image: $K6_DOCKER_IMAGE"
    K6_CMD="docker run --rm --network host -v $LOAD_TEST_DIR:/scripts -v $RESULTS_DIR:/results -e BASE_URL=$BASE_URL -e WS_URL=$WS_URL $K6_DOCKER_IMAGE"
    return 0
  else
    log_error "Neither k6 nor Docker is available. Please install one of them."
    exit 1
  fi
}

# Create results directory
mkdir -p "$RESULTS_DIR"

# Run load test
run_test() {
  local test_file=$1
  local test_name=$(basename "$test_file" .js)
  local timestamp=$(date +%Y%m%d_%H%M%S)
  local result_file="$RESULTS_DIR/${test_name}_${timestamp}.json"
  
  log_info "Running test: $test_name"
  log_info "Target: $BASE_URL"
  
  if [ "$K6_CMD" = "k6" ]; then
    k6 run \
      --out json="$result_file" \
      --summary-export="$RESULTS_DIR/${test_name}_${timestamp}_summary.json" \
      -e BASE_URL="$BASE_URL" \
      -e WS_URL="$WS_URL" \
      "$test_file"
  else
    # Docker command
    docker run --rm \
      --network host \
      -v "$LOAD_TEST_DIR:/scripts" \
      -v "$RESULTS_DIR:/results" \
      -e BASE_URL="$BASE_URL" \
      -e WS_URL="$WS_URL" \
      "$K6_DOCKER_IMAGE" \
      run \
      --out json="/results/${test_name}_${timestamp}.json" \
      --summary-export="/results/${test_name}_${timestamp}_summary.json" \
      "/scripts/$test_name.js"
  fi
  
  if [ $? -eq 0 ]; then
    log_success "Test completed: $test_name"
  else
    log_error "Test failed: $test_name"
    return 1
  fi
}

# Analyze results
analyze_results() {
  log_info "Analyzing load test results..."
  
  if [ -f "$SCRIPT_DIR/analyze-load-results.sh" ]; then
    bash "$SCRIPT_DIR/analyze-load-results.sh" "$RESULTS_DIR"
  else
    log_warning "Result analysis script not found. Skipping analysis."
  fi
}

# Main execution
main() {
  log_info "═══════════════════════════════════════════════════════"
  log_info "K6 Load Testing - Atlasia AI Orchestrator"
  log_info "═══════════════════════════════════════════════════════"
  log_info "Environment: $ENVIRONMENT"
  log_info "Base URL: $BASE_URL"
  log_info "WebSocket URL: $WS_URL"
  log_info "Test Scenario: $TEST_SCENARIO"
  log_info "═══════════════════════════════════════════════════════"
  
  check_k6
  
  case "$TEST_SCENARIO" in
    api)
      run_test "$LOAD_TEST_DIR/api-load-test.js"
      ;;
    a2a)
      run_test "$LOAD_TEST_DIR/a2a-load-test.js"
      ;;
    websocket)
      run_test "$LOAD_TEST_DIR/websocket-load-test.js"
      ;;
    all)
      log_info "Running all load tests..."
      run_test "$LOAD_TEST_DIR/api-load-test.js"
      run_test "$LOAD_TEST_DIR/a2a-load-test.js"
      run_test "$LOAD_TEST_DIR/websocket-load-test.js"
      ;;
    *)
      log_error "Unknown test scenario: $TEST_SCENARIO"
      echo "Valid scenarios: api, a2a, websocket, all"
      exit 1
      ;;
  esac
  
  analyze_results
  
  log_success "═══════════════════════════════════════════════════════"
  log_success "Load testing complete!"
  log_success "Results saved to: $RESULTS_DIR"
  log_success "═══════════════════════════════════════════════════════"
}

main
