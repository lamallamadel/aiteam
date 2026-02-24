#!/bin/bash
# Container Security Scanning Script
# Scans Docker images for vulnerabilities using Trivy

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "======================================"
echo "Container Security Scanning with Trivy"
echo "======================================"

# Check if Trivy is installed
if ! command -v trivy &> /dev/null; then
    echo -e "${RED}Error: Trivy is not installed${NC}"
    echo "Install Trivy: https://aquasecurity.github.io/trivy/latest/getting-started/installation/"
    exit 1
fi

# Configuration
TRIVY_CONFIG="infra/trivy-config.yaml"
SEVERITY="HIGH,CRITICAL"
OUTPUT_DIR="security-reports"

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo ""
echo "Configuration:"
echo "  Config: $TRIVY_CONFIG"
echo "  Severity: $SEVERITY"
echo "  Output: $OUTPUT_DIR/"
echo ""

# Function to scan image
scan_image() {
    local image_name=$1
    local report_prefix=$2
    
    echo -e "${YELLOW}Scanning $image_name...${NC}"
    
    # Scan and generate table output
    echo "  → Running vulnerability scan..."
    trivy image \
        --config "$TRIVY_CONFIG" \
        --severity "$SEVERITY" \
        --format table \
        "$image_name" | tee "$OUTPUT_DIR/${report_prefix}-table.txt"
    
    # Generate JSON report
    echo "  → Generating JSON report..."
    trivy image \
        --config "$TRIVY_CONFIG" \
        --severity "$SEVERITY" \
        --format json \
        --output "$OUTPUT_DIR/${report_prefix}-report.json" \
        "$image_name"
    
    # Generate SARIF report for GitHub
    echo "  → Generating SARIF report..."
    trivy image \
        --config "$TRIVY_CONFIG" \
        --severity "$SEVERITY" \
        --format sarif \
        --output "$OUTPUT_DIR/${report_prefix}-sarif.json" \
        "$image_name"
    
    # Check for CRITICAL vulnerabilities
    CRITICAL_COUNT=$(trivy image \
        --config "$TRIVY_CONFIG" \
        --severity CRITICAL \
        --format json \
        "$image_name" 2>/dev/null | \
        jq '[.Results[]?.Vulnerabilities[]? | select(.Severity=="CRITICAL")] | length' 2>/dev/null || echo "0")
    
    if [ "$CRITICAL_COUNT" -gt 0 ]; then
        echo -e "${RED}  ✗ CRITICAL: Found $CRITICAL_COUNT CRITICAL vulnerabilities${NC}"
        return 1
    else
        echo -e "${GREEN}  ✓ No CRITICAL vulnerabilities found${NC}"
        return 0
    fi
}

# Build images if needed
BUILD_IMAGES=false
if [ "$1" == "--build" ]; then
    BUILD_IMAGES=true
    echo -e "${YELLOW}Building images...${NC}"
fi

# Backend image
BACKEND_IMAGE="ai-orchestrator:local"
if [ "$BUILD_IMAGES" == true ]; then
    echo "Building backend image..."
    cd ai-orchestrator
    docker build -t "$BACKEND_IMAGE" .
    cd ..
fi

# Frontend image
FRONTEND_IMAGE="frontend:local"
if [ "$BUILD_IMAGES" == true ]; then
    echo "Building frontend image..."
    cd frontend
    docker build -t "$FRONTEND_IMAGE" .
    cd ..
fi

echo ""
echo "======================================"
echo "Scanning Backend Image"
echo "======================================"
BACKEND_RESULT=0
scan_image "$BACKEND_IMAGE" "backend" || BACKEND_RESULT=$?

echo ""
echo "======================================"
echo "Scanning Frontend Image"
echo "======================================"
FRONTEND_RESULT=0
scan_image "$FRONTEND_IMAGE" "frontend" || FRONTEND_RESULT=$?

# Summary
echo ""
echo "======================================"
echo "Scan Summary"
echo "======================================"
echo ""
echo "Reports saved to: $OUTPUT_DIR/"
echo "  - backend-table.txt     (human-readable)"
echo "  - backend-report.json   (machine-readable)"
echo "  - backend-sarif.json    (GitHub Security)"
echo "  - frontend-table.txt"
echo "  - frontend-report.json"
echo "  - frontend-sarif.json"
echo ""

if [ $BACKEND_RESULT -eq 0 ] && [ $FRONTEND_RESULT -eq 0 ]; then
    echo -e "${GREEN}✓ All scans passed - No CRITICAL vulnerabilities${NC}"
    exit 0
else
    echo -e "${RED}✗ CRITICAL vulnerabilities detected${NC}"
    echo ""
    echo "Action required:"
    echo "  1. Review detailed reports in $OUTPUT_DIR/"
    echo "  2. Update base images or dependencies"
    echo "  3. Or add suppression rule to infra/trivy-config.yaml"
    echo ""
    exit 1
fi
