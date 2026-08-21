# GCP Secret Manager Integration Guide
## AI Contract Analyzer

---

## Overview

We integrated **Google Cloud Secret Manager** to securely manage all sensitive credentials (database passwords, Redis passwords, connection URLs) instead of hardcoding them in config files or Docker environment variables.

---

## Before vs After

### BEFORE (Insecure)
```yaml
# application.yml — passwords visible in plain text!
spring:
  datasource:
    password: postgres    ← anyone reading the repo sees this

# docker-compose.yml — also exposed
environment:
  SPRING_DATASOURCE_PASSWORD: postgres   ← visible in Git history forever
```

### AFTER (Secure with GCP Secret Manager)
```yaml
# application-gcp.yml — only secret NAMES, never values
spring:
  datasource:
    password: ${sm://db-password}   ← fetched at runtime from GCP vault
```

The actual password lives **only** in GCP Secret Manager — encrypted, access-controlled, audited.

---

## How It Works

```
┌─────────────────────────────────────────────────────────┐
│                    STARTUP FLOW                           │
│                                                           │
│  1. Spring Boot starts with --spring.profiles.active=gcp │
│                       ↓                                   │
│  2. Sees spring.cloud.gcp.secretmanager.enabled=true     │
│                       ↓                                   │
│  3. Finds ${sm://db-password} in application-gcp.yml     │
│                       ↓                                   │
│  4. Calls GCP Secret Manager API:                        │
│     GET /projects/PROJECT/secrets/db-password/latest      │
│                       ↓                                   │
│  5. GCP authenticates the VM's service account           │
│     (automatic on Compute Engine/Cloud Run)               │
│                       ↓                                   │
│  6. Returns decrypted secret value: "SuperStr0ngP@ss!"   │
│                       ↓                                   │
│  7. Spring injects it into spring.datasource.password    │
│                       ↓                                   │
│  8. HikariCP connects to DB using the real password      │
└─────────────────────────────────────────────────────────┘
```

---

## Files Changed

| File | Change |
|------|--------|
| `pom.xml` | Added `spring-cloud-gcp-starter-secretmanager` dependency + BOM |
| `application.yml` | Added `spring.config.import: optional:sm://` + GCP config |
| `application-gcp.yml` (NEW) | GCP profile with `${sm://secret-name}` references |
| `scripts/gcp-secrets-setup.sh` (NEW) | One-time script to create all secrets in GCP |

---

## Secrets Stored in GCP Secret Manager

| Secret Name | Purpose | Example Value |
|-------------|---------|---------------|
| `db-connection-url` | PostgreSQL JDBC URL | `jdbc:postgresql://db:5432/contractdb` |
| `db-username` | Database username | `postgres` |
| `db-password` | Database password | `SuperStr0ngP@ss!` |
| `redis-host` | Redis server hostname | `redis` |
| `redis-password` | Redis AUTH password | `RedisP@ss123` |
| `ollama-base-url` | Ollama API endpoint | `http://ollama:11434` |

---

## Setup Steps

### Step 1: Enable the API

```bash
gcloud services enable secretmanager.googleapis.com
```

### Step 2: Create secrets (run the setup script)

```bash
chmod +x scripts/gcp-secrets-setup.sh
./scripts/gcp-secrets-setup.sh
```

Or create individually:
```bash
echo -n "MySecurePassword123" | gcloud secrets create db-password --data-file=-
```

### Step 3: Grant access to your VM's service account

```bash
# Get the service account email
SA_EMAIL=$(gcloud iam service-accounts list --filter="displayName:Compute Engine" --format="value(email)")

# Grant accessor role
gcloud projects add-iam-policy-binding YOUR_PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/secretmanager.secretAccessor"
```

### Step 4: Deploy with GCP profile active

```bash
# In Docker Compose (add to backend environment):
SPRING_PROFILES_ACTIVE: gcp
GCP_SECRET_MANAGER_ENABLED: true
GCP_PROJECT_ID: contract-analyser-spring-ai-v1

# Or in Kubernetes ConfigMap:
SPRING_PROFILES_ACTIVE: "gcp"
```

---

## Spring Profiles Explained

| Profile | When Used | How Secrets Are Resolved |
|---------|-----------|--------------------------|
| **default** (no profile) | Local dev, Docker Compose | Environment variables or hardcoded defaults |
| **gcp** | GCP Compute Engine, Cloud Run | Fetched from GCP Secret Manager via `${sm://}` |

```bash
# Local development (uses defaults from application.yml):
./mvnw spring-boot:run

# GCP deployment (uses GCP Secret Manager):
java -jar app.jar --spring.profiles.active=gcp
```

---

## How `${sm://secret-name}` Works

The Spring Cloud GCP Secret Manager starter adds a custom **PropertySource**:

1. Spring sees `${sm://db-password}` during property resolution
2. The `sm://` prefix triggers the GCP Secret Manager property source
3. It calls the Secret Manager API: `projects/PROJECT_ID/secrets/db-password/versions/latest`
4. GCP returns the decrypted value
5. Spring substitutes it into the config

**Authentication** happens automatically on GCP infrastructure:
- On Compute Engine: uses the VM's attached service account
- On Cloud Run: uses the service's IAM identity
- Locally: uses `gcloud auth application-default login` credentials

---

## Security Benefits

| Feature | Without Secret Manager | With Secret Manager |
|---------|----------------------|---------------------|
| Secrets in Git | ❌ YES (visible forever in history) | ✅ NO (only names, never values) |
| Encryption | ❌ Plain text | ✅ AES-256 at rest |
| Access control | ❌ Anyone with repo access | ✅ IAM roles per secret |
| Audit trail | ❌ None | ✅ Full log of who accessed what |
| Rotation | ❌ Manual redeploy | ✅ Add new version, app picks up automatically |
| Shared across envs | ❌ Copy-paste per environment | ✅ Different secrets per project/env |

---

## Useful Commands

```bash
# List all secrets
gcloud secrets list

# View a secret's value
gcloud secrets versions access latest --secret=db-password

# Update a secret (add new version)
echo -n "NewPassword456!" | gcloud secrets versions add db-password --data-file=-

# Delete a secret
gcloud secrets delete db-password

# View access audit log
gcloud logging read 'resource.type="secretmanager.googleapis.com/Secret"' --limit=10
```

---

## Interview Key Points

1. **`sm://` prefix** = Spring Cloud GCP convention for Secret Manager references
2. **Profiles** = `application-gcp.yml` only loads when `spring.profiles.active=gcp`
3. **No code changes** = Secrets are resolved at property level, Java code never touches them
4. **Automatic auth** = On GCP VMs, service account authentication is implicit (no API keys)
5. **`optional:sm://`** = Doesn't fail if Secret Manager is unavailable (graceful fallback for local dev)

---

*End of Document*
