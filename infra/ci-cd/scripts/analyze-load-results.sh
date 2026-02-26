#!/bin/bash
# ─────────────────────────────────────────────────────────────
# K6 Load Test Results Analysis Script
# 
# Analyzes k6 JSON output and checks against performance baselines
# to detect regressions.
# ─────────────────────────────────────────────────────────────

set -e

RESULTS_DIR="${1:-./ai-orchestrator/target/load-test-results}"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# Performance baselines
API_P95_BASELINE=500        # 500ms
AI_P95_BASELINE=2000        # 2s
WS_CONNECTION_BASELINE=1000 # 1s
WS_MESSAGE_BASELINE=100     # 100ms
ERROR_RATE_BASELINE=0.01    # 1%

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

# Check if jq is available
if ! command -v jq &> /dev/null; then
  log_warning "jq not found. Installing for JSON parsing..."
  if command -v apt-get &> /dev/null; then
    sudo apt-get install -y jq
  elif command -v yum &> /dev/null; then
    sudo yum install -y jq
  else
    log_error "Cannot install jq. Please install manually."
    exit 1
  fi
fi

# Find latest summary files
find_latest_summary() {
  local test_type=$1
  find "$RESULTS_DIR" -name "${test_type}*_summary.json" -type f -printf '%T@ %p\n' | sort -n | tail -1 | cut -d' ' -f2-
}

# Analyze metrics from summary
analyze_summary() {
  local summary_file=$1
  local test_name=$2
  
  if [ ! -f "$summary_file" ]; then
    log_warning "Summary file not found: $summary_file"
    return 1
  fi
  
  log_info "Analyzing $test_name results..."
  
  # Extract key metrics
  local http_req_duration_p95=$(jq -r '.metrics.http_req_duration.values.p95 // 0' "$summary_file")
  local http_req_failed_rate=$(jq -r '.metrics.http_req_failed.values.rate // 0' "$summary_file")
  local iterations=$(jq -r '.metrics.iterations.values.count // 0' "$summary_file")
  local vus_max=$(jq -r '.metrics.vus_max.values.max // 0' "$summary_file")
  
  echo "  📊 Metrics:"
  echo "     - HTTP Request Duration (p95): ${http_req_duration_p95}ms"
  echo "     - HTTP Request Failed Rate: $(awk "BEGIN {printf \"%.2f%%\", $http_req_failed_rate * 100}")"
  echo "     - Total Iterations: $iterations"
  echo "     - Max VUs: $vus_max"
  
  # Check against baselines
  local regression=false
  
  # API latency check
  if (( $(echo "$http_req_duration_p95 > $API_P95_BASELINE" | bc -l) )); then
    log_error "REGRESSION: p95 latency ($http_req_duration_p95 ms) exceeds baseline ($API_P95_BASELINE ms)"
    regression=true
  else
    log_success "p95 latency within baseline"
  fi
  
  # Error rate check
  if (( $(echo "$http_req_failed_rate > $ERROR_RATE_BASELINE" | bc -l) )); then
    log_error "REGRESSION: Error rate ($http_req_failed_rate) exceeds baseline ($ERROR_RATE_BASELINE)"
    regression=true
  else
    log_success "Error rate within baseline"
  fi
  
  return $([ "$regression" = true ] && echo 1 || echo 0)
}

# Generate HTML report
generate_html_report() {
  local output_file="$RESULTS_DIR/load-test-report.html"
  
  cat > "$output_file" <<EOF
<!DOCTYPE html>
<html>
<head>
  <title>K6 Load Test Results - Atlasia</title>
  <style>
    body { font-family: Arial, sans-serif; margin: 20px; background: #f5f5f5; }
    .container { max-width: 1200px; margin: 0 auto; background: white; padding: 20px; border-radius: 8px; }
    h1 { color: #333; border-bottom: 3px solid #4CAF50; padding-bottom: 10px; }
    h2 { color: #555; margin-top: 30px; }
    .metric { background: #e8f5e9; padding: 15px; margin: 10px 0; border-radius: 4px; border-left: 4px solid #4CAF50; }
    .metric.warning { background: #fff3e0; border-left-color: #ff9800; }
    .metric.error { background: #ffebee; border-left-color: #f44336; }
    .metric-label { font-weight: bold; color: #333; }
    .metric-value { font-size: 1.2em; color: #555; }
    table { width: 100%; border-collapse: collapse; margin: 20px 0; }
    th, td { padding: 12px; text-align: left; border-bottom: 1px solid #ddd; }
    th { background: #4CAF50; color: white; }
    tr:hover { background: #f5f5f5; }
    .pass { color: #4CAF50; font-weight: bold; }
    .fail { color: #f44336; font-weight: bold; }
  </style>
</head>
<body>
  <div class="container">
    <h1>🚀 K6 Load Test Results</h1>
    <p>Generated: $(date)</p>
    
    <h2>Test Summary</h2>
    <p>Results directory: $RESULTS_DIR</p>
    
    <div id="results">
      <!-- Results will be populated here -->
      <p>Processing test results...</p>
    </div>
    
    <h2>Performance Baselines</h2>
    <table>
      <tr>
        <th>Metric</th>
        <th>Baseline</th>
        <th>Description</th>
      </tr>
      <tr>
        <td>API p95 Latency</td>
        <td>&lt; ${API_P95_BASELINE}ms</td>
        <td>95th percentile response time for API calls</td>
      </tr>
      <tr>
        <td>AI p95 Latency</td>
        <td>&lt; ${AI_P95_BASELINE}ms</td>
        <td>95th percentile response time for AI agent responses</td>
      </tr>
      <tr>
        <td>WebSocket Connection</td>
        <td>&lt; ${WS_CONNECTION_BASELINE}ms</td>
        <td>Time to establish WebSocket connection</td>
      </tr>
      <tr>
        <td>WebSocket Message Latency</td>
        <td>&lt; ${WS_MESSAGE_BASELINE}ms</td>
        <td>Message round-trip time</td>
      </tr>
      <tr>
        <td>Error Rate</td>
        <td>&lt; ${ERROR_RATE_BASELINE}%</td>
        <td>Percentage of failed requests</td>
      </tr>
    </table>
  </div>
</body>
</html>
EOF
  
  log_success "HTML report generated: $output_file"
}

# Main execution
main() {
  log_info "═══════════════════════════════════════════════════════"
  log_info "K6 Load Test Results Analysis"
  log_info "═══════════════════════════════════════════════════════"
  
  if [ ! -d "$RESULTS_DIR" ]; then
    log_error "Results directory not found: $RESULTS_DIR"
    exit 1
  fi
  
  local regression_detected=false
  
  # Analyze API load test
  local api_summary=$(find_latest_summary "api-load-test")
  if [ -n "$api_summary" ]; then
    if ! analyze_summary "$api_summary" "API Load Test"; then
      regression_detected=true
    fi
  fi
  
  # Analyze A2A load test
  local a2a_summary=$(find_latest_summary "a2a-load-test")
  if [ -n "$a2a_summary" ]; then
    if ! analyze_summary "$a2a_summary" "A2A Load Test"; then
      regression_detected=true
    fi
  fi
  
  # Analyze WebSocket load test
  local ws_summary=$(find_latest_summary "websocket-load-test")
  if [ -n "$ws_summary" ]; then
    if ! analyze_summary "$ws_summary" "WebSocket Load Test"; then
      regression_detected=true
    fi
  fi
  
  # Generate HTML report
  generate_html_report
  
  log_info "═══════════════════════════════════════════════════════"
  
  if [ "$regression_detected" = true ]; then
    log_error "Performance regression detected!"
    exit 1
  else
    log_success "All performance baselines met!"
    exit 0
  fi
}

main
