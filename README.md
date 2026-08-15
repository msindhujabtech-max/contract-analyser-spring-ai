# AI Contract Analyzer

A production-ready, containerized RAG (Retrieval-Augmented Generation) pipeline for analyzing multi-page PDF contracts using local AI models.

## Architecture

- **Backend**: Java 21 + Spring Boot 3.3 (Reactive WebFlux)
- **AI Orchestration**: Spring AI with ChatClient fluent API
- **Local AI**: Ollama (`llama3` for chat, `nomic-embed-text` for embeddings)
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

- First startup may take several minutes as Ollama downloads the AI models (~4GB for llama3, ~270MB for nomic-embed-text).
- The system seeds a default contract entry (ID: 1, User: 101) for immediate evaluation.
- Multi-tenant isolation is enforced via metadata filtering on vector similarity searches.
