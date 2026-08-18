# GCP Firewall Issue & Fix — AI Contract Analyzer

---

## Problem Statement

After deploying the AI Contract Analyzer on Google Cloud VM (Compute Engine), the application URL `http://34.70.230.73` was:
- ✅ Accessible from the deployer's laptop
- ❌ NOT accessible from a colleague's laptop (corporate network)

---

## Root Cause

**Two layers of firewall were blocking access:**

### Layer 1: GCP VPC Firewall (Cloud-side)
Google Cloud blocks ALL incoming traffic by default except SSH (port 22). Custom firewall rules must be created to allow traffic on ports 3000 (frontend) and 8000 (backend).

### Layer 2: Corporate Network Firewall (Client-side)
HCL's corporate network firewall blocks outbound connections to **non-standard ports**. Only ports 80 (HTTP) and 443 (HTTPS) are allowed through corporate proxies.

This means even after opening ports 3000/8000 on GCP, corporate users still couldn't connect because their own network blocks those ports.

---

## Diagnosis Steps Performed

### Step 1: Verified containers were running
```bash
docker ps
```
**Result**: All 4 containers (frontend, backend, db, ollama) running and healthy.

### Step 2: Verified ports bound to 0.0.0.0
```bash
netstat -tlnp | grep -E "3000|8000"
```
**Result**:
```
tcp   0   0   0.0.0.0:3000   0.0.0.0:*   LISTEN
tcp   0   0   0.0.0.0:8000   0.0.0.0:*   LISTEN
```
Ports bound to `0.0.0.0` = accepting connections from all interfaces (not just localhost). ✅

### Step 3: Created GCP Firewall Rule
```bash
gcloud compute firewall-rules create allow-contract-analyzer --direction=INGRESS --action=ALLOW --rules=tcp:3000,tcp:8000 --source-ranges=0.0.0.0/0
```

### Step 4: Verified Firewall Rule Applied
```bash
gcloud compute firewall-rules list --filter="name=allow-contract-analyzer"
```
**Result**:
```
NAME: allow-contract-analyzer
NETWORK: default
DIRECTION: INGRESS
PRIORITY: 1000
ALLOW: tcp:3000,tcp:8000
SOURCE_RANGES: 0.0.0.0/0
DISABLED: False
```
Rule exists and is active. ✅

### Step 5: Still Not Working — Identified Corporate Firewall
Even with GCP firewall open, the colleague's laptop (on HCL corporate network) could not reach port 3000. Corporate firewalls typically only allow outbound traffic on port 80 and 443.

---

## Fix Applied

Mapped the frontend container to **port 80** instead of port 3000:

```bash
docker stop contract-frontend
docker run -d --name contract-frontend-80 -p 80:3000 --network contract-analyser-spring-ai_default contract-analyser-spring-ai-frontend
```

**What this does**:
- `-p 80:3000` — Maps host port 80 to container port 3000
- Port 80 is standard HTTP — allowed by all corporate firewalls
- Users access `http://34.70.230.73` (no port needed, 80 is the default)

---

## Permanent Fix (docker-compose.yml)

To make this persist across restarts, update the `docker-compose.yml`:

**Before:**
```yaml
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: contract-frontend
    ports:
      - "3000:3000"
```

**After:**
```yaml
  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: contract-frontend
    ports:
      - "80:3000"
```

Then restart:
```bash
docker compose down
docker compose up -d
```

---

## Also Needed: GCP Firewall Rule for Port 80

```bash
gcloud compute firewall-rules create allow-http --direction=INGRESS --action=ALLOW --rules=tcp:80 --source-ranges=0.0.0.0/0 --network=default
```

Or if you already have the `allow-contract-analyzer` rule, update it:
```bash
gcloud compute firewall-rules update allow-contract-analyzer --rules=tcp:80,tcp:3000,tcp:8000
```

---

## Summary

| Issue | Cause | Fix |
|-------|-------|-----|
| Not accessible at all | GCP VPC firewall blocks all ports by default | Create ingress firewall rule |
| Accessible from home, not from office | Corporate network blocks non-standard ports (3000, 8000) | Map frontend to port 80 |

---

## Key Takeaways for Interview

1. **GCP blocks all incoming traffic by default** — you must explicitly create firewall rules
2. **Corporate networks only allow ports 80 (HTTP) and 443 (HTTPS)** — always deploy user-facing services on standard ports
3. **`0.0.0.0` vs `127.0.0.1`** — services must bind to `0.0.0.0` to accept external connections
4. **Docker port mapping format**: `-p HOST_PORT:CONTAINER_PORT`
5. **Firewall rules need `--source-ranges=0.0.0.0/0`** to allow traffic from any IP address
6. **Production best practice**: Use a reverse proxy (nginx) on port 80/443 to route to internal services on any port

---

*Issue resolved on: August 2026*
*VM: contract-analyzer-vm (us-central1-a)*
*Project: contract-analyser-spring-ai-v1*
