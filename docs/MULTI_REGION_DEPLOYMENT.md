# Multi-Region CRDT Deployment Guide

This guide covers deploying the Atlasia AI Orchestrator across multiple regions with CRDT-based collaboration support.

## Prerequisites

- Kubernetes cluster in each region (or equivalent container orchestration)
- Shared PostgreSQL database (or regional replicas with eventual consistency)
- Network connectivity between regions (VPN/VPC peering for WebSocket mesh)
- Load balancers with WebSocket support

## Architecture

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│   US-EAST-1     │◄───────►│  EU-CENTRAL-1   │◄───────►│  AP-SOUTHEAST-1 │
│                 │  CRDT   │                 │  CRDT   │                 │
│  Orchestrator   │  Sync   │  Orchestrator   │  Sync   │  Orchestrator   │
│                 │         │                 │         │                 │
│  PostgreSQL     │         │  PostgreSQL     │         │  PostgreSQL     │
└─────────────────┘         └─────────────────┘         └─────────────────┘
        │                           │                           │
        └───────────────────────────┴───────────────────────────┘
                       Shared or Replicated Database
```

## Region Configuration

### 1. US-EAST-1 (Primary)

**Environment Variables:**
```bash
export REGION="us-east-1"
export CRDT_MESH_PEERS="wss://eu-central-1.atlasia.ai/ws,wss://ap-southeast-1.atlasia.ai/ws"
export DB_URL="jdbc:postgresql://primary-db.us-east-1.rds.amazonaws.com:5432/atlasia"
```

**Kubernetes Deployment:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ai-orchestrator
  namespace: atlasia
spec:
  replicas: 3
  selector:
    matchLabels:
      app: ai-orchestrator
  template:
    metadata:
      labels:
        app: ai-orchestrator
        region: us-east-1
    spec:
      containers:
      - name: orchestrator
        image: atlasia/ai-orchestrator:latest
        env:
        - name: REGION
          value: "us-east-1"
        - name: CRDT_MESH_PEERS
          value: "wss://eu-central-1.atlasia.ai/ws,wss://ap-southeast-1.atlasia.ai/ws"
        - name: DB_URL
          valueFrom:
            secretKeyRef:
              name: database-config
              key: url
        ports:
        - containerPort: 8080
          protocol: TCP
```

### 2. EU-CENTRAL-1

**Environment Variables:**
```bash
export REGION="eu-central-1"
export CRDT_MESH_PEERS="wss://us-east-1.atlasia.ai/ws,wss://ap-southeast-1.atlasia.ai/ws"
export DB_URL="jdbc:postgresql://eu-replica-db.eu-central-1.rds.amazonaws.com:5432/atlasia"
```

### 3. AP-SOUTHEAST-1

**Environment Variables:**
```bash
export REGION="ap-southeast-1"
export CRDT_MESH_PEERS="wss://us-east-1.atlasia.ai/ws,wss://eu-central-1.atlasia.ai/ws"
export DB_URL="jdbc:postgresql://ap-replica-db.ap-southeast-1.rds.amazonaws.com:5432/atlasia"
```

## Network Setup

### VPC Peering (AWS Example)

1. **Create Peering Connections:**
```bash
# US-EAST-1 <-> EU-CENTRAL-1
aws ec2 create-vpc-peering-connection \
  --vpc-id vpc-us-east-1 \
  --peer-vpc-id vpc-eu-central-1 \
  --peer-region eu-central-1

# US-EAST-1 <-> AP-SOUTHEAST-1
aws ec2 create-vpc-peering-connection \
  --vpc-id vpc-us-east-1 \
  --peer-vpc-id vpc-ap-southeast-1 \
  --peer-region ap-southeast-1

# EU-CENTRAL-1 <-> AP-SOUTHEAST-1
aws ec2 create-vpc-peering-connection \
  --vpc-id vpc-eu-central-1 \
  --peer-vpc-id vpc-ap-southeast-1 \
  --peer-region ap-southeast-1
```

2. **Update Route Tables:**
```bash
aws ec2 create-route \
  --route-table-id rtb-us-east-1 \
  --destination-cidr-block 10.1.0.0/16 \
  --vpc-peering-connection-id pcx-us-eu
```

3. **Security Groups:**
```bash
# Allow WebSocket connections (port 8080) from peer regions
aws ec2 authorize-security-group-ingress \
  --group-id sg-orchestrator \
  --protocol tcp \
  --port 8080 \
  --source-group sg-orchestrator-eu \
  --group-owner eu-account-id
```

## Load Balancer Configuration

### NGINX WebSocket Proxy

```nginx
upstream orchestrator_backend {
    server orchestrator-1:8080;
    server orchestrator-2:8080;
    server orchestrator-3:8080;
}

server {
    listen 443 ssl http2;
    server_name us-east-1.atlasia.ai;

    ssl_certificate /etc/ssl/certs/atlasia.crt;
    ssl_certificate_key /etc/ssl/private/atlasia.key;

    location /ws/ {
        proxy_pass http://orchestrator_backend;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 86400;
    }

    location / {
        proxy_pass http://orchestrator_backend;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

## Database Setup

### Option 1: Single Primary Database

Use AWS RDS with read replicas in each region:

```bash
# Primary in US-EAST-1
aws rds create-db-instance \
  --db-instance-identifier atlasia-primary \
  --db-instance-class db.r6g.xlarge \
  --engine postgres \
  --master-username admin \
  --master-user-password <secret> \
  --allocated-storage 100 \
  --region us-east-1

# Read replica in EU-CENTRAL-1
aws rds create-db-instance-read-replica \
  --db-instance-identifier atlasia-eu-replica \
  --source-db-instance-identifier arn:aws:rds:us-east-1:...:db:atlasia-primary \
  --region eu-central-1
```

### Option 2: Multi-Master PostgreSQL

Use Citus or similar for distributed writes:

```sql
-- Enable Citus extension
CREATE EXTENSION citus;

-- Add worker nodes
SELECT citus_add_node('eu-db.atlasia.ai', 5432);
SELECT citus_add_node('ap-db.atlasia.ai', 5432);

-- Distribute tables
SELECT create_distributed_table('collaboration_events', 'run_id');
SELECT create_distributed_table('crdt_snapshots', 'run_id');
```

## Monitoring

### Prometheus Metrics

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
data:
  prometheus.yml: |
    scrape_configs:
    - job_name: 'orchestrator-us-east-1'
      static_configs:
      - targets: ['orchestrator-us-east-1.atlasia.ai:8080']
        labels:
          region: 'us-east-1'
    
    - job_name: 'orchestrator-eu-central-1'
      static_configs:
      - targets: ['orchestrator-eu-central-1.atlasia.ai:8080']
        labels:
          region: 'eu-central-1'
    
    - job_name: 'orchestrator-ap-southeast-1'
      static_configs:
      - targets: ['orchestrator-ap-southeast-1.atlasia.ai:8080']
        labels:
          region: 'ap-southeast-1'
```

### Key Metrics to Monitor

- `crdt_sync_messages_total{region="us-east-1"}`: Sync message throughput
- `crdt_sync_failures_total{region="us-east-1"}`: Failed syncs
- `crdt_mesh_peers_connected{region="us-east-1"}`: Mesh health
- `crdt_lamport_clock{run_id="..."}`: Logical clock progression

## Testing Multi-Region Setup

### 1. Verify Mesh Connectivity

```bash
curl https://us-east-1.atlasia.ai/api/crdt/mesh/status
# Expected: {"region": "us-east-1", "status": "active"}

curl https://eu-central-1.atlasia.ai/api/crdt/mesh/status
# Expected: {"region": "eu-central-1", "status": "active"}
```

### 2. Test Cross-Region Sync

```bash
# Create graft in US-EAST-1
RUN_ID="123e4567-e89b-12d3-a456-426614174000"
curl -X POST https://us-east-1.atlasia.ai/app/runs/$RUN_ID/graft \
  -H "Content-Type: application/json" \
  -d '{"after": "step1", "agentName": "SecurityAgent"}'

# Verify state in EU-CENTRAL-1 (should propagate within 100ms)
sleep 0.2
curl https://eu-central-1.atlasia.ai/api/crdt/runs/$RUN_ID/state

# Expected: grafts array contains SecurityAgent
```

### 3. Partition Tolerance Test

```bash
# Simulate network partition
iptables -A INPUT -s <eu-central-1-ip> -j DROP
iptables -A OUTPUT -d <eu-central-1-ip> -j DROP

# Continue making changes in US-EAST-1
curl -X POST https://us-east-1.atlasia.ai/app/runs/$RUN_ID/graft \
  -d '{"after": "step2", "agentName": "PerformanceAgent"}'

# Make conflicting changes in EU-CENTRAL-1
curl -X POST https://eu-central-1.atlasia.ai/app/runs/$RUN_ID/graft \
  -d '{"after": "step2", "agentName": "ReliabilityAgent"}'

# Restore network
iptables -D INPUT -s <eu-central-1-ip> -j DROP
iptables -D OUTPUT -d <eu-central-1-ip> -j DROP

# Wait for convergence (should happen automatically)
sleep 1

# Verify both regions have same final state
curl https://us-east-1.atlasia.ai/api/crdt/runs/$RUN_ID/state
curl https://eu-central-1.atlasia.ai/api/crdt/runs/$RUN_ID/state

# Expected: Both contain PerformanceAgent AND ReliabilityAgent (deterministic order)
```

## Disaster Recovery

### Snapshot Restoration

If a region fails completely:

```bash
# Get latest snapshot from surviving region
curl https://eu-central-1.atlasia.ai/api/crdt/runs/$RUN_ID/changes > snapshot.bin

# Restore to new region instance
curl -X POST https://us-west-2.atlasia.ai/api/crdt/runs/$RUN_ID/sync \
  -H "Content-Type: application/json" \
  -d "{\"changes\": \"$(base64 snapshot.bin)\", \"sourceRegion\": \"eu-central-1\", \"lamportTimestamp\": 12345}"
```

### Database Failover

Configure automatic failover with RDS:

```bash
aws rds modify-db-instance \
  --db-instance-identifier atlasia-primary \
  --multi-az \
  --apply-immediately
```

## Cost Optimization

- **WebSocket Keep-Alive**: Set to 30s to reduce bandwidth
- **Snapshot Frequency**: Adjust `SNAPSHOT_INTERVAL_EVENTS` based on activity
- **Compression**: Enable gzip for WebSocket messages
- **Regional Affinity**: Route users to nearest region for lower latency

## Security

- **TLS 1.3**: Required for all WebSocket connections
- **JWT Auth**: Validate tokens at mesh layer
- **Rate Limiting**: 1000 mutations/minute per user per region
- **IP Allowlisting**: Restrict mesh connections to known peer IPs

## Troubleshooting

### Peers Not Connecting

```bash
# Check logs
kubectl logs -n atlasia deployment/ai-orchestrator | grep "CRDT mesh"

# Verify DNS resolution
nslookup eu-central-1.atlasia.ai

# Test WebSocket connection
wscat -c wss://eu-central-1.atlasia.ai/ws
```

### State Divergence

```bash
# Force full sync from primary
curl -X POST https://us-east-1.atlasia.ai/api/crdt/runs/$RUN_ID/register-peer \
  -d '{"peerRegion": "eu-central-1"}'
```

### High Sync Latency

- Check network latency between regions
- Verify load balancer WebSocket timeout settings
- Monitor database replication lag
- Check for firewall/NAT issues

## References

- AWS VPC Peering: https://docs.aws.amazon.com/vpc/latest/peering/
- Kubernetes Multi-Cluster: https://kubernetes.io/docs/concepts/cluster-administration/federation/
- PostgreSQL Replication: https://www.postgresql.org/docs/current/high-availability.html
