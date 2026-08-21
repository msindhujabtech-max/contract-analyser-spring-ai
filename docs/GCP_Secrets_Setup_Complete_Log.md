# GCP Secret Manager — Complete Setup Log
## All Commands, Passwords, Issues, and Fixes

---

## Project Details

| Field | Value |
|-------|-------|
| GCP Project ID | `contract-analyser-spring-ai-v1` |
| VM Name | `contract-analyzer-vm` |
| VM Zone | `us-central1-a` |
| Service Account | `693850766295-compute@developer.gserviceaccount.com` |
| VM IP | `34.70.230.73` |

---

## All Secrets Stored in GCP Secret Manager

| Secret Name | Value | Used For |
|-------------|-------|----------|
| `db-connection-url` | `jdbc:postgresql://db:5432/contractdb` | PostgreSQL JDBC connection |
| `db-username` | `postgres` | Database username |
| `db-password` | `postgres` | Database password |
| `redis-host` | `redis` | Redis server hostname |
| `redis-password` | *(empty)* | Redis AUTH password (none set) |
| `ollama-base-url` | `http://ollama:11434` | Ollama AI model server endpoint |

---

## Step-by-Step Commands Executed

### Step 1: Enable Secret Manager API

```bash
gcloud services enable secretmanager.googleapis.com
```
**Result**: Success

---

### Step 2: Create All Secrets

```bash
echo -n "jdbc:postgresql://db:5432/contractdb" | gcloud secrets create db-connection-url --data-file=-

echo -n "postgres" | gcloud secrets create db-username --data-file=-

echo -n "postgres" | gcloud secrets create db-password --data-file=-

echo -n "redis" | gcloud secrets create redis-host --data-file=-

echo -n "" | gcloud secrets create redis-password --data-file=-

echo -n "http://ollama:11434" | gcloud secrets create ollama-base-url --data-file=-
```
**Result**: All 6 secrets created successfully

---

### Step 3: Verify Secrets Were Created

```bash
gcloud secrets list
```

**Output**:
```
NAME: db-connection-url
CREATED: 2026-08-21T12:30:21
REPLICATION_POLICY: automatic

NAME: db-password
CREATED: 2026-08-21T12:30:53
REPLICATION_POLICY: automatic

NAME: db-username
CREATED: 2026-08-21T12:30:39
REPLICATION_POLICY: automatic

NAME: ollama-base-url
CREATED: 2026-08-21T12:31:20
REPLICATION_POLICY: automatic

NAME: redis-host
CREATED: 2026-08-21T12:31:03
REPLICATION_POLICY: automatic

NAME: redis-password
CREATED: 2026-08-21T12:31:13
REPLICATION_POLICY: automatic
```

---

### Step 4: Verify Secret Value

```bash
gcloud secrets versions access latest --secret=db-password
```

**Output**: `postgres`

---

### Step 5: Grant VM Access to Secrets (THE ISSUE)

#### ❌ FAILED Command (PowerShell `$()` doesn't work):

```powershell
gcloud projects add-iam-policy-binding contract-analyser-spring-ai-v1 --member="serviceAccount:$(gcloud iam service-accounts list --filter='displayName:Compute Engine' --format='value(email)')" --role="roles/secretmanager.secretAccessor"
```

**Error**:
```
ERROR: (gcloud.projects.add-iam-policy-binding) INVALID_ARGUMENT: Invalid service account ().
```

**Root Cause**: PowerShell (Windows) does NOT support bash's `$(...)` subshell syntax. The inner command returned empty, resulting in `serviceAccount:` (empty email).

#### ✅ FIX — Two-Step Approach:

**Step 5a: Get the service account email manually**:
```bash
gcloud iam service-accounts list
```

**Output**:
```
DISPLAY NAME: Default compute service account
EMAIL: 693850766295-compute@developer.gserviceaccount.com
DISABLED: False
```

**Step 5b: Use the email directly**:
```bash
gcloud projects add-iam-policy-binding contract-analyser-spring-ai-v1 --member="serviceAccount:693850766295-compute@developer.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
```

**Result**: Success
```
Updated IAM policy for project [contract-analyser-spring-ai-v1].
bindings:
- members:
  - serviceAccount:693850766295-compute@developer.gserviceaccount.com
  role: roles/secretmanager.secretAccessor
```

---

## Issue Summary

| # | Issue | Cause | Fix |
|---|-------|-------|-----|
| 1 | `INVALID_ARGUMENT: Invalid service account ()` | PowerShell doesn't support `$(...)` bash syntax | Split into 2 commands: first get email, then use it explicitly |

---

## Deployment Commands

### Deploy WITH Secret Manager (production):

```bash
cd ~/contract-analyser-spring-ai && git pull && docker compose down && SPRING_PROFILES_ACTIVE=gcp GCP_PROJECT_ID=contract-analyser-spring-ai-v1 docker compose up --build -d
```

### Deploy WITHOUT Secret Manager (normal Docker Compose):

```bash
cd ~/contract-analyser-spring-ai && git pull && docker compose down && docker compose up --build -d
```

---

## How Secrets Are Referenced in Code

### application-gcp.yml (activated with `--spring.profiles.active=gcp`):

```yaml
spring:
  datasource:
    url: ${sm://db-connection-url}       # Fetches from GCP Secret Manager
    username: ${sm://db-username}         # Fetches from GCP Secret Manager
    password: ${sm://db-password}         # Fetches from GCP Secret Manager
  data:
    redis:
      host: ${sm://redis-host}            # Fetches from GCP Secret Manager
      password: ${sm://redis-password}    # Fetches from GCP Secret Manager
  ai:
    ollama:
      base-url: ${sm://ollama-base-url}   # Fetches from GCP Secret Manager
```

### application.yml (default — used in Docker Compose / local dev):

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/contractdb}
    username: ${SPRING_DATASOURCE_USERNAME:postgres}
    password: ${SPRING_DATASOURCE_PASSWORD:postgres}
```

---

## Useful Management Commands

### View a secret value:
```bash
gcloud secrets versions access latest --secret=db-password
```

### Update a secret (add new version):
```bash
echo -n "NewStrongPassword123!" | gcloud secrets versions add db-password --data-file=-
```

### Delete a secret:
```bash
gcloud secrets delete db-password
```

### List all secret versions:
```bash
gcloud secrets versions list db-password
```

### Disable a specific version:
```bash
gcloud secrets versions disable 1 --secret=db-password
```

### View access audit logs:
```bash
gcloud logging read 'protoPayload.serviceName="secretmanager.googleapis.com"' --limit=10
```

### Revoke access from service account:
```bash
gcloud projects remove-iam-policy-binding contract-analyser-spring-ai-v1 --member="serviceAccount:693850766295-compute@developer.gserviceaccount.com" --role="roles/secretmanager.secretAccessor"
```

---

## Files Related to Secret Manager

| File | Purpose |
|------|---------|
| `backend/pom.xml` | Added `spring-cloud-gcp-starter-secretmanager` dependency |
| `backend/src/main/resources/application.yml` | Default config + `spring.config.import: optional:sm://` |
| `backend/src/main/resources/application-gcp.yml` | GCP profile with `${sm://}` secret references |
| `scripts/gcp-secrets-setup.sh` | One-time setup script for creating all secrets |
| `docs/GCP_Secret_Manager_Integration.md` | Detailed integration guide |

---

## Key Learnings

1. **PowerShell ≠ Bash**: `$(command)` syntax doesn't work in PowerShell. Use backticks or split into separate commands.
2. **`sm://` prefix**: Spring Cloud GCP convention — tells Spring to fetch the value from Secret Manager.
3. **Service account needs explicit access**: VMs don't automatically have Secret Manager access — must grant `roles/secretmanager.secretAccessor`.
4. **Profiles separate environments**: `application.yml` = local/Docker, `application-gcp.yml` = cloud with secrets.
5. **`optional:sm://`**: The `optional:` prefix means the app won't crash if Secret Manager is unavailable (graceful fallback for local dev).

---

*Setup completed: August 21, 2026*
*GCP Project: contract-analyser-spring-ai-v1*
