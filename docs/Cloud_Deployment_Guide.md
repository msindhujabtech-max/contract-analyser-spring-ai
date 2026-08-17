# Cloud Deployment Guide — AI Contract Analyzer (Free Tier)

---

## ⚠️ Reality Check: The LLM Challenge

This project uses **Ollama with Llama3** (a 4GB+ model) which requires significant GPU/RAM resources. No free cloud tier offers GPU instances for free. Here are your realistic options:

---

## OPTION A: Hybrid Approach (RECOMMENDED — Truly Free)

**Run the LLM locally, deploy everything else in the cloud.**

| Component | Where | Free Tier |
|-----------|-------|-----------|
| Frontend | Vercel / Netlify | ✅ Free forever |
| Backend | Render.com / Railway | ✅ Free tier (750 hrs/month) |
| Database (pgvector) | Neon.tech / Supabase | ✅ Free tier |
| Ollama (LLM) | Your local machine | ✅ Free |

### Step-by-Step:

#### Step 1: Database — Neon.tech (Free PostgreSQL + pgvector)

1. Go to https://neon.tech and sign up (GitHub login)
2. Create a new project → select **PostgreSQL 16**
3. Neon supports pgvector out of the box
4. Run the `schema.sql` in the Neon SQL Editor:
   - Go to "SQL Editor" in the dashboard
   - Paste the contents of `db/schema.sql`
   - Execute
5. Copy the connection string: `postgresql://user:pass@ep-xxx.us-east-2.aws.neon.tech/contractdb?sslmode=require`

**Free tier limits**: 512 MB storage, 0.25 vCPU, always-on

#### Step 2: Backend — Render.com (Free Web Service)

1. Go to https://render.com and sign up
2. Click "New +" → "Web Service"
3. Connect your GitHub repo: `msindhujabtech-max/contract-analyser-spring-ai`
4. Configure:
   - **Name**: `contract-analyzer-api`
   - **Root Directory**: `backend`
   - **Runtime**: Docker
   - **Instance Type**: Free
5. Add Environment Variables:
   ```
   SPRING_DATASOURCE_URL=jdbc:postgresql://ep-xxx.neon.tech/contractdb?sslmode=require
   SPRING_DATASOURCE_USERNAME=<from Neon>
   SPRING_DATASOURCE_PASSWORD=<from Neon>
   SPRING_AI_OLLAMA_BASE_URL=http://localhost:11434
   SPRING_AI_OLLAMA_CHAT_MODEL=llama3
   SPRING_AI_OLLAMA_EMBEDDING_MODEL=nomic-embed-text
   ```
6. Deploy

**Note**: The Ollama URL won't work from Render unless you expose your local Ollama via a tunnel (see Step 4).

#### Step 3: Frontend — Vercel (Free Static Hosting)

1. Go to https://vercel.com and sign up with GitHub
2. Click "Add New Project" → Import `contract-analyser-spring-ai`
3. Configure:
   - **Framework Preset**: Vite
   - **Root Directory**: `frontend`
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
4. Add Environment Variable:
   - `VITE_API_BASE=https://contract-analyzer-api.onrender.com`
5. Deploy

**Update `App.jsx`** to use the environment variable:
```javascript
const API_BASE = import.meta.env.VITE_API_BASE || 'http://localhost:8000';
```

#### Step 4: Expose Local Ollama via Tunnel (Free)

Since no free cloud offers GPU, run Ollama locally and expose it:

```bash
# Install Ollama locally: https://ollama.com/download
ollama serve

# In another terminal, pull models:
ollama pull llama3
ollama pull nomic-embed-text

# Expose via Cloudflare Tunnel (free):
# Install cloudflared: https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/
cloudflared tunnel --url http://localhost:11434
```

This gives you a public URL like `https://xxx-yyy.trycloudflare.com`. Update your Render backend env:
```
SPRING_AI_OLLAMA_BASE_URL=https://xxx-yyy.trycloudflare.com
```

**Alternative tunnel**: ngrok (free tier)
```bash
ngrok http 11434
```

---

## OPTION B: Fully Cloud-Based (Replace Ollama with Free API)

**Replace local Ollama with a free cloud LLM API.**

### Use Groq Cloud (Free, Fast, No GPU Needed)

Groq offers free API access to Llama3:
- Sign up: https://console.groq.com
- Free tier: 30 requests/minute, 14,400 requests/day
- Models: llama3-8b, llama3-70b

**Code changes needed** in the backend:

1. Replace `spring-ai-ollama-spring-boot-starter` with a REST-based approach
2. Or use Spring AI's OpenAI-compatible client pointed at Groq's endpoint

**application.yml changes**:
```yaml
spring:
  ai:
    openai:
      base-url: https://api.groq.com/openai/v1
      api-key: ${GROQ_API_KEY}
      chat:
        options:
          model: llama3-8b-8192
```

**Embedding alternative** (since Groq doesn't do embeddings):
- Use **HuggingFace Inference API** (free tier) for embeddings
- Or use **Voyage AI** (free 50M tokens/month)

**This approach requires code modifications** — I can help if you choose this path.

---

## OPTION C: Full Docker Deploy on Free VM

### Oracle Cloud Always Free Tier (Best Free Option for Full Stack)

Oracle offers a **permanently free** ARM VM with 24GB RAM — enough to run everything including Ollama.

1. Sign up: https://cloud.oracle.com (credit card required but never charged for Always Free)
2. Create an **Ampere A1 Compute** instance:
   - Shape: VM.Standard.A1.Flex
   - **4 OCPUs + 24 GB RAM** (free forever!)
   - OS: Ubuntu 22.04
3. SSH into the instance and install Docker:
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-plugin
   sudo usermod -aG docker $USER
   ```
4. Clone and run:
   ```bash
   git clone https://github.com/msindhujabtech-max/contract-analyser-spring-ai.git
   cd contract-analyser-spring-ai
   docker compose up --build -d
   ```
5. Open firewall ports (Security List in OCI console):
   - Port 3000 (frontend)
   - Port 8000 (backend)

**Access**: `http://<your-vm-public-ip>:3000`

**Pros**: Everything runs as designed, no code changes
**Cons**: ARM architecture (some Docker images may need rebuilding), Llama3 on CPU is slow (~2-3 tokens/sec)

---

## OPTION D: Google Cloud Run (300$ Free Credits for 90 Days)

Google gives $300 free credits on signup (enough for ~3 months of testing).

1. Sign up: https://cloud.google.com (credit card required)
2. Enable Cloud Run, Cloud SQL, Artifact Registry
3. Deploy database:
   ```bash
   gcloud sql instances create contractdb --database-version=POSTGRES_16 --tier=db-f1-micro --region=us-central1
   ```
4. Build & push backend:
   ```bash
   gcloud builds submit --tag gcr.io/YOUR_PROJECT/contract-backend ./backend
   gcloud run deploy contract-backend --image gcr.io/YOUR_PROJECT/contract-backend --port 8080
   ```
5. For Ollama: Still need local tunnel or use Groq API

---

## Comparison Table

| Option | Cost | LLM Speed | Code Changes | Complexity |
|--------|------|-----------|--------------|------------|
| A: Hybrid (local Ollama) | $0 | Fast (your GPU) | Minimal (API_BASE env) | Medium |
| B: Groq Cloud API | $0 | Very Fast | Moderate (swap AI provider) | Medium |
| C: Oracle Free VM | $0 | Slow (CPU only) | None | Low |
| D: Google Cloud | $0 for 90 days | Depends | None | High |

---

## My Recommendation for Interview Demo

**Go with Option A (Hybrid)** if you have a decent machine:
- Database on Neon.tech (free, instant setup)
- Frontend on Vercel (free, auto-deploys from GitHub)
- Backend on Render (free, auto-deploys from GitHub)
- Ollama on your laptop + Cloudflare tunnel

**Total cost: $0. Setup time: ~30 minutes.**

If you want zero local dependencies and accept slower responses, **Option C (Oracle Cloud VM)** gives you the full Docker stack running 24/7 for free.

---

## Quick Setup Commands (Option A)

```bash
# 1. Install Ollama locally
# Download from https://ollama.com/download

# 2. Pull models
ollama pull nomic-embed-text
ollama pull llama3

# 3. Start Ollama
ollama serve

# 4. Expose via tunnel (new terminal)
npx cloudflared tunnel --url http://localhost:11434
# Note the public URL

# 5. Database: Create on https://neon.tech (UI-based, 2 minutes)

# 6. Backend: Deploy on https://render.com (connect GitHub, 5 minutes)

# 7. Frontend: Deploy on https://vercel.com (connect GitHub, 3 minutes)
```

---

*End of Guide*
