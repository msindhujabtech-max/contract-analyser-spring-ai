# AI Contract Analyzer

A production-ready, containerized RAG (Retrieval-Augmented Generation) pipeline for analyzing multi-page PDF contracts using local AI models.

## Architecture

- **Backend**: Java 21 + Spring Boot 3.3 (Reactive WebFlux)
- **AI Orchestration**: Spring AI with ChatClient fluent API
- **Local AI**: Ollama (`llama3.2:3b` for chat, `nomic-embed-text` for embeddings)
- **Database**: PostgreSQL 16 + pgvector (768 dimensions)
- **Frontend**: React 18 + Vite
- **Orchestration**: Docker Compose

## Quick Start

```bash
docker-compose up --build
```

Services:
- Frontend: http://localhost:3000
- Backend API: http://localhost:8000
- PostgreSQL: localhost:5432
- Ollama: localhost:11434

## Redeploy on Google Cloud

Run the following command from Google Cloud Shell to pull the latest `main` branch, rebuild the Docker images, redeploy the existing VM stack, and verify Redis. It assumes the repository is cloned at `~/contract-analyser-spring-ai` on the VM.

```bash
gcloud compute ssh contract-analyzer-vm --zone us-central1-a --project contract-analyser-spring-ai-v1 --command='set -e; cd ~/contract-analyser-spring-ai; git pull origin main; docker compose up -d --build; docker compose ps; docker exec contract-redis redis-cli ping; docker exec contract-redis redis-cli CLIENT LIST; docker exec contract-redis redis-cli --scan --pattern "rag:response:*"; docker exec contract-redis redis-cli --scan --pattern "chat:history:*"'
```

Expected results include a `PONG` response from Redis and a `lib-name=Lettuce` connection in the Redis client list. The deployed frontend is available at http://34.70.230.73:3000.

## API Endpoints

### POST /api/upload
Upload a PDF contract for processing and indexing.

```bash
curl -X POST http://localhost:8000/api/upload \
  -F "file=@contract.pdf"
```

### POST /api/chat/stream
Stream AI responses about the uploaded contract.

```bash
curl -X POST http://localhost:8000/api/chat/stream \
  -H "Content-Type: application/json" \
  -d '{"contract_id": 1, "user_id": 101, "question": "What are the payment terms?"}'
```

## Development

### Backend
```bash
cd backend
./mvnw spring-boot:run
```

### Frontend
```bash
cd frontend
npm install
npm run dev
```

## Notes

- First startup may take several minutes as Ollama downloads the AI models (~2GB for llama3.2:3b, ~270MB for nomic-embed-text).
- The system seeds a default contract entry (ID: 1, User: 101) for immediate evaluation.
- Multi-tenant isolation is enforced via metadata filtering on vector similarity searches.
