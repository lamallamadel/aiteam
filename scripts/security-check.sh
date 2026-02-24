#!/bin/bash
# Container Security Status Check
# Verifies runtime security configurations for running containers

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

COMPOSE_FILE="infra/docker-compose.ai.yml"

echo "======================================"
echo "Container Security Status Check"
echo "======================================"
echo ""

# Check if containers are running
if ! docker-compose -f "$COMPOSE_FILE" ps | grep -q "Up"; then
    echo -e "${RED}Error: No containers running${NC}"
    echo "Start containers with: docker-compose -f $COMPOSE_FILE up -d"
    exit 1
fi

PASS_COUNT=0
FAIL_COUNT=0

# Function to check status
check() {
    local description=$1
    local command=$2
    local expected=$3
    
    echo -e "${BLUE}Checking: $description${NC}"
    result=$(eval "$command" 2>/dev/null || echo "ERROR")
    
    if [ "$result" == "$expected" ]; then
        echo -e "${GREEN}  ✓ PASS${NC}"
        PASS_COUNT=$((PASS_COUNT + 1))
    else
        echo -e "${RED}  ✗ FAIL${NC}"
        echo "    Expected: $expected"
        echo "    Got: $result"
        FAIL_COUNT=$((FAIL_COUNT + 1))
    fi
    echo ""
}

# Check user for each service
echo "======================================"
echo "1. Non-Root User Verification"
echo "======================================"
echo ""

for service in ai-orchestrator vault; do
    if docker-compose -f "$COMPOSE_FILE" ps | grep -q "$service.*Up"; then
        echo -e "${YELLOW}Service: $service${NC}"
        uid=$(docker-compose -f "$COMPOSE_FILE" exec -T "$service" id -u 2>/dev/null || echo "error")
        
        if [ "$uid" != "0" ] && [ "$uid" != "error" ]; then
            echo -e "${GREEN}  ✓ Running as non-root user (UID: $uid)${NC}"
            PASS_COUNT=$((PASS_COUNT + 1))
        else
            echo -e "${RED}  ✗ Running as root or check failed${NC}"
            FAIL_COUNT=$((FAIL_COUNT + 1))
        fi
        echo ""
    fi
done

# Check capabilities
echo "======================================"
echo "2. Dropped Capabilities"
echo "======================================"
echo ""

for service in ai-orchestrator vault; do
    container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
    if [ -n "$container_id" ]; then
        echo -e "${YELLOW}Service: $service${NC}"
        
        # Check if CapDrop includes ALL
        cap_drop=$(docker inspect "$container_id" | jq -r '.[0].HostConfig.CapDrop[]' 2>/dev/null | grep -q "ALL" && echo "ALL" || echo "NONE")
        
        if [ "$cap_drop" == "ALL" ]; then
            echo -e "${GREEN}  ✓ All capabilities dropped${NC}"
            PASS_COUNT=$((PASS_COUNT + 1))
        else
            echo -e "${YELLOW}  ⚠ Not all capabilities dropped${NC}"
            FAIL_COUNT=$((FAIL_COUNT + 1))
        fi
        
        # Show added capabilities
        cap_add=$(docker inspect "$container_id" | jq -r '.[0].HostConfig.CapAdd[]?' 2>/dev/null | tr '\n' ',' | sed 's/,$//')
        if [ -n "$cap_add" ]; then
            echo "    Added: $cap_add"
        fi
        echo ""
    fi
done

# Check read-only filesystem
echo "======================================"
echo "3. Read-Only Root Filesystem"
echo "======================================"
echo ""

for service in ai-orchestrator; do
    container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
    if [ -n "$container_id" ]; then
        echo -e "${YELLOW}Service: $service${NC}"
        
        readonly=$(docker inspect "$container_id" | jq -r '.[0].HostConfig.ReadonlyRootfs' 2>/dev/null)
        
        if [ "$readonly" == "true" ]; then
            echo -e "${GREEN}  ✓ Read-only root filesystem enabled${NC}"
            PASS_COUNT=$((PASS_COUNT + 1))
        else
            echo -e "${YELLOW}  ⚠ Read-only root filesystem not enabled${NC}"
            FAIL_COUNT=$((FAIL_COUNT + 1))
        fi
        echo ""
    fi
done

# Check security options
echo "======================================"
echo "4. Security Options"
echo "======================================"
echo ""

for service in ai-orchestrator vault; do
    container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
    if [ -n "$container_id" ]; then
        echo -e "${YELLOW}Service: $service${NC}"
        
        sec_opts=$(docker inspect "$container_id" | jq -r '.[0].HostConfig.SecurityOpt[]?' 2>/dev/null | tr '\n' ',' | sed 's/,$//')
        
        if echo "$sec_opts" | grep -q "no-new-privileges:true"; then
            echo -e "${GREEN}  ✓ no-new-privileges enabled${NC}"
            PASS_COUNT=$((PASS_COUNT + 1))
        else
            echo -e "${YELLOW}  ⚠ no-new-privileges not enabled${NC}"
            FAIL_COUNT=$((FAIL_COUNT + 1))
        fi
        
        if [ -n "$sec_opts" ]; then
            echo "    Options: $sec_opts"
        fi
        echo ""
    fi
done

# Check resource limits
echo "======================================"
echo "5. Resource Limits"
echo "======================================"
echo ""

for service in ai-orchestrator ai-db vault; do
    container_id=$(docker-compose -f "$COMPOSE_FILE" ps -q "$service" 2>/dev/null)
    if [ -n "$container_id" ]; then
        echo -e "${YELLOW}Service: $service${NC}"
        
        memory_limit=$(docker inspect "$container_id" | jq -r '.[0].HostConfig.Memory' 2>/dev/null)
        cpu_quota=$(docker inspect "$container_id" | jq -r '.[0].HostConfig.CpuQuota' 2>/dev/null)
        
        if [ "$memory_limit" != "0" ]; then
            memory_gb=$(echo "scale=2; $memory_limit / 1073741824" | bc)
            echo -e "${GREEN}  ✓ Memory limit: ${memory_gb}GB${NC}"
            PASS_COUNT=$((PASS_COUNT + 1))
        else
            echo -e "${YELLOW}  ⚠ No memory limit set${NC}"
            FAIL_COUNT=$((FAIL_COUNT + 1))
        fi
        
        if [ "$cpu_quota" != "0" ] && [ "$cpu_quota" != "-1" ]; then
            echo -e "${GREEN}  ✓ CPU quota set${NC}"
        else
            echo -e "${YELLOW}  ⚠ No CPU quota set${NC}"
        fi
        echo ""
    fi
done

# Check current resource usage
echo "======================================"
echo "6. Current Resource Usage"
echo "======================================"
echo ""

docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}\t{{.NetIO}}\t{{.BlockIO}}"

echo ""

# Summary
echo "======================================"
echo "Security Check Summary"
echo "======================================"
echo ""
echo -e "Passed: ${GREEN}$PASS_COUNT${NC}"
echo -e "Failed: ${RED}$FAIL_COUNT${NC}"
echo ""

if [ $FAIL_COUNT -eq 0 ]; then
    echo -e "${GREEN}✓ All security checks passed${NC}"
    exit 0
else
    echo -e "${YELLOW}⚠ Some security checks failed${NC}"
    echo ""
    echo "Review the failures above and update docker-compose.ai.yml if needed."
    echo "See docs/CONTAINER_SECURITY.md for security hardening guidelines."
    echo ""
    exit 1
fi
