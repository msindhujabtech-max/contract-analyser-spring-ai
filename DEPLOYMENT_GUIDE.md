# Deployment Guide — AI Contract Analyzer + Audit Service

This guide lets anyone deploy the complete project on Google Cloud in under 15 minutes.
No Java, no IDE, no manual GCP Console clicking — just Terraform + Docker.

---

## What Gets Deployed

```
┌─────────────────────────── GCP VM (contract-analyzer-vm) ───────────────────────────┐
│                                                                                      │
│  ┌──────────────┐  ┌──────────────┐  ┌────────────────────┐  ┌──────────────────┐  │
│  │   Frontend   │  │   Backend    │  │  Audit Service     │  │     Ollama       │  │
│  │  React UI    │  │  Spring Boot │  │  Spring Boot       │  │  LLM + Embed     │  │
│  │  Port: 3000  │  │  Port: 8000  │  │  Port: 8082        │  │  Port: 11434     │  │
│  └──────────────┘  └──────────────┘  └────────────────────┘  └──────────────────┘  │
│                                                                                      │
│  ┌──────────────┐  ┌──────────────┐                                                 │
│  │  PostgreSQL  │  │    Redis     │          All on: contract-network               │
│  │  + pgvector  │  │    Cache     │                                                 │
│  │  Port: 5432  │  │  Port: 6379  │                                                 │
│  └──────────────┘  └──────────────┘                                                 │
└──────────────────────────────────────────────────────────────────────────────────────┘
```

---

## Prerequisites (One-Time Setup on Your Laptop)

| Tool | What it does | Install link |
|------|-------------|--------------|
| **Google Cloud SDK** (`gcloud`) | Authenticates with GCP | https://cloud.google.com/sdk/docs/install |
| **Terraform** | Creates cloud infrastructure from code | https://developer.hashicorp.com/terraform/install |
| **Git** | Clones the project repos | https://git-scm.com/downloads |

### GCP Account Requirements
- A Google Cloud account with **billing enabled**
- A GCP project (create one at https://console.cloud.google.com)
- Compute Engine API enabled (Terraform will prompt you if it's not)

---

## Option A: Deploy with Terraform (Recommended — Fully Automated)

### Step 1: Authenticate with Google Cloud

```bash
gcloud auth login
gcloud auth application-default login
gcloud config set project YOUR-GCP-PROJECT-ID
```

### Step 2: Clone this repo

```bash
git clone https://github.com/msindhujabtech-max/contract-analyser-spring-ai.git
cd contract-analyser-spring-ai/terraform
```

### Step 3: Set your GCP project ID

```bash
cp terraform.tfvars.example terraform.tfvars
```

Open `terraform.tfvars` and replace `YOUR-GCP-PROJECT-ID-HERE` with your actual project ID:

```hcl
project_id = "my-actual-project-id"
```

### Step 4: Deploy

```bash
terraform init
terraform plan
terraform apply
```

Type `yes` when prompted. Wait ~10-15 minutes.

### Step 5: Get your URLs

After completion, Terraform prints:

```
frontend_url      = "http://XX.XX.XX.XX:3000"
backend_url       = "http://XX.XX.XX.XX:8000"
audit_service_url = "http://XX.XX.XX.XX:8082"
ssh_command       = "gcloud compute ssh contract-analyzer-vm --zone=us-central1-a"
```

### Step 6: Wait for services to start

The VM startup script downloads Docker images and builds containers (~10 min). Monitor progress:

```bash
gcloud compute ssh contract-analyzer-vm --zone=us-central1-a --command="sudo journalctl -u google-startup-scripts -f"
```

Or SSH in and check:

```bash
gcloud compute ssh contract-analyzer-vm --zone=us-central1-a
docker ps
```

You should see **6 containers** running:
- `contract-frontend` (port 3000)
- `contract-backend` (port 8000→8080)
- `contract-audit-service` (port 8082)
- `contract-db` (port 5432)
- `contract-redis` (port 6379)
- `contract-ollama` (port 11434)

### Step 7: Open browser

Go to `http://YOUR-IP:3000` → Upload a PDF → Ask questions!

---

## Option B: Manual Deployment (If you already have a VM)

### Step 1: SSH into your VM

```bash
gcloud compute ssh contract-analyzer-vm --zone=us-central1-a
```

### Step 2: Create the shared Docker network

```bash
docker network create contract-network
```

### Step 3: Deploy audit service (port 8082) — deploy this FIRST

```bash
cd ~
git clone https://github.com/msindhujabtech-max/contract-audit-service.git
cd ~/contract-audit-service
docker compose up -d --build
```

### Step 4: Deploy analyser service (port 8000 + 3000)

```bash
cd ~
git clone https://github.com/msindhujabtech-max/contract-analyser-spring-ai.git
cd ~/contract-analyser-spring-ai
docker compose up -d --build
```

### Step 5: Verify

```bash
docker ps
```

All 6 containers should be running.

### Step 6: Open the firewall (if not already done)

```bash
gcloud compute firewall-rules create allow-contract-app-traffic --allow tcp:3000,tcp:8000,tcp:8082 --source-ranges=0.0.0.0/0 --target-tags=http-server
```

---

## Updating After Code Changes

SSH into the VM and rebuild:

```bash
gcloud compute ssh contract-analyzer-vm --zone=us-central1-a
```

```bash
# Rebuild audit service
cd ~/contract-audit-service && git pull origin main && docker compose down && docker compose up -d --build

# Rebuild analyser (backend + frontend)
cd ~/contract-analyser-spring-ai && git pull origin main && docker compose down && docker compose up -d --build
```

**Important:** Always rebuild the audit service FIRST, then the analyser.

---

## Tearing Down (Stop Billing)

### If using Terraform:

```bash
cd contract-analyser-spring-ai/terraform
terraform destroy
```

Type `yes`. This deletes the VM, IP, and firewall rules.

### If deployed manually:

```bash
gcloud compute instances delete contract-analyzer-vm --zone=us-central1-a
gcloud compute addresses delete contract-app-ip --region=us-central1
gcloud compute firewall-rules delete allow-contract-app-traffic
```

---

## Troubleshooting

| Problem | Solution |
|---------|----------|
| `terraform apply` fails with "API not enabled" | Run: `gcloud services enable compute.googleapis.com` |
| Containers not running after 15 min | SSH in: `sudo journalctl -u google-startup-scripts` |
| Port not accessible from browser | Check: `gcloud compute firewall-rules list` |
| `docker compose` not found | VM startup may still be running — wait a few minutes |
| Ollama models not loaded yet | Check: `docker logs contract-ollama` (first pull ~5 min) |
| Frontend loads but upload fails | Backend still starting: `docker logs contract-backend --tail 20` |
| Audit status not showing in frontend | Verify audit container: `docker logs contract-audit-service` |

---

## Project Repositories

| Repo | Purpose |
|------|---------|
| https://github.com/msindhujabtech-max/contract-analyser-spring-ai | Main app: Frontend + Backend + AI/RAG + Terraform |
| https://github.com/msindhujabtech-max/contract-audit-service | Audit microservice (called by main backend) |

---

## Architecture & Service Communication

| Service | Internal URL (Docker DNS) | External URL |
|---------|--------------------------|--------------|
| Frontend | http://contract-frontend:3000 | http://YOUR-IP:3000 |
| Backend API | http://contract-backend:8080 | http://YOUR-IP:8000 |
| Audit Service | http://contract-audit-service:8082 | http://YOUR-IP:8082 |
| PostgreSQL | postgresql://db:5432 | Not exposed |
| Redis | redis://redis:6379 | Not exposed |
| Ollama LLM | http://ollama:11434 | Not exposed |

---

## Cost Estimate

Running on `e2-standard-4` in `us-central1`:
- **~$0.13/hour** (~$97/month if running 24/7)
- **Tip:** `terraform destroy` when not using it to stop charges!
