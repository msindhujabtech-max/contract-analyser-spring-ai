https://hclo365-my.sharepoint.com/:f:/r/personal/sindhuja_m_hcltech_com1/Documents/Sindhuja_51991295_PairProgramming_Jul2026?d=wcb57479725ed47a8b42e0cd6afc59289&csf=1&web=1&e=RkC5mx


List of Assignments
1. Code description and documentation, refactoring - Java code
• Generate code for any program (with some complexity, not too simple one).
• Generate the documentation for the code (Getting comments added to the
method, class level)
• Refactor the code to improve readability / to reduce complexity / to use
better logic.
2. Code conversion – one language to another.
• Generate code for any program in one language
• Rewrite the above code in different language
3. Conversion of Older Version to Newer Version of Java
• Generate code for any program (with some complexity, not too simple one) in
a specific version of Java (eg. Java version <8)
• Rewrite the same code in a later version (eg. Java version 17)
4. Code Optimization - Code consuming more system resources like CPU, Memory or
more time in execution
• Give a specific code snippet and understand the existing complexity of the
code from the space and time complexity perspective
• Rewrite the optimized code
5. Identify and fix the problem with the given code
• Provide the code having problem/issue. Take the assistance of GenAI to fix
the problem/issue.
6. Create Java application for - Inventory Management system
• After the application is created, check the possibility to improve the
application (Review and improve)
• Generate Document (Add comments to make the code more readable)
• Run the application
• Generate Unit tests (Junit), run and make all tests pass.

Prompt engineering case study

Prmopt 1:

Create a plain Java console-based Maven project without using any frameworks like Spring or Spring Boot. The application must feature the functionalities of a realistic Hospital Management System (HMS), including
adding new patient,
deleting patient,
updating patient,
retrieving all patient,
and searching for a specific patient by name, patient ID, or dob.
Patient entity can have - patient  name , gender, patient id,dob, location,
The project should use an H2 database alongside the JUnit test framework, utilizing the latest stable versions of all software and test frameworks.
Additionally, the code must include appropriate comments for documentation and readability,
 feature proper exception handling, and include a class with a main method providing an interactive menu to run the application.
Use slf4j logger
Use stream API
Write global exception handler to take care of exception handling
Maintain the configuration in a separate file
 
Add the logging at key places in the application
 
 Finally, ensure you write a comprehensive set of positive and negative test cases to validate all the functionalities of the HMS.



 Prompt 2:

 Act as a Senior Fullstack AI Engineer (FDE). Build a containerized, production-ready "AI Contract Analyzer" application that processes multi-page PDF documents and allows users to query them via a real-time, streaming RAG (Retrieval-Augmented Generation) pipeline.

Strictly adhere to the following architecture, tech stack, and feature requirements:

### 1. Technology Stack
- Backend: Python 3.11 with FastAPI (Asynchronous execution framework)
- Database: PostgreSQL 16 using the official 'pgvector/pgvector:pg16' image
- Frontend: React 18 with Vite (Modern JavaScript/ESM, vanilla CSS/JS inline styles, strictly NO external heavy UI frameworks)
- AI & Embeddings: Native OpenAI Python SDK ('text-embedding-3-small' for 1536-dimension embeddings and 'gpt-4o-mini' for rapid chat completions)
- Orchestration: Docker Compose linking frontend, backend, and database networks smoothly

### 2. Feature & Architectural Requirements

#### A. Database Initialization (pgvector)
- Provide a SQL script ('init.sql') that runs automatically when the database initializes.
- Enable the 'vector' extension.
- Create a 'contracts' metadata table (id, user_id, filename, uploaded_at).
- Create a 'contract_chunks' table (id, contract_id, content TEXT, embedding vector(1536)).
- Add a foreign key linking 'contract_chunks' to 'contracts' with a cascade delete.
- Implement a high-performance HNSW index ('using hnsw (embedding vector_cosine_ops)') for rapid semantic lookup.
- Seed the table with a default contract entry (ID: 1, User ID: 101) to allow immediate evaluation.

#### B. Backend API Layer (FastAPI)
- Implement CORS middleware to allow cross-origin requests from the React local client.
- Endpoint 1: POST '/api/upload'
  - Accepts a raw PDF file via UploadFile.
  - Extracts text contents using 'pypdf'.
  - Implements a fixed-size chunking strategy (1000 characters chunk size, 200 characters overlap).
  - Iterates over chunks, generates embeddings using OpenAI, and inserts them into PostgreSQL.
  - Cleans out previous vector data for Contract ID 1 before storing new data.
- Endpoint 2: POST '/api/chat/stream'
  - Accepts a JSON payload containing 'contract_id', 'user_id', and 'question'.
  - Generates an embedding for the user's question.
  - Performs a vector similarity search using cosine distance ('<=>') to extract top 3 matching chunks.
  - Enforces database-layer row security using an explicit SQL JOIN with the 'contracts' table to verify that the 'user_id' owns the 'contract_id'.
  - Leverages 'StreamingResponse' to stream raw LLM tokens back to the client using Server-Sent Events (SSE) format ('data: <token>\n\n').
  - Implements strict system guardrails instructing the LLM to reply "I cannot find that information in the contract." if the retrieved context lacks the answer.

#### C. Frontend User Interface (React)
- Create a single-page responsive dashboard broken into two distinct segments.
- Segment 1: Document Loader. Includes a file input tracking '.pdf' uploads, communicating with '/api/upload', and displaying step-by-step loading state messages.
- Segment 2: RAG Query Chatbox. An interactive messaging console displaying historical chat logs.
- The chatbox must use the browser's native 'fetch' and 'ReadableStream' reader (via text/event-stream) to append tokens dynamically to the UI as they stream from FastAPI.
- Implement an automatic scroll-to-bottom anchor using a React 'useRef' hook to smoothly update the viewing plane as chunks arrive.

#### D. Container Configuration (Docker Compose)
- Construct a 'docker-compose.yml' grouping 'db', 'backend', and 'frontend' services.
- Establish a 'healthcheck' on the database container ensuring it is fully operational ('pg_isready') before the backend triggers.
- Map the backend to port 8000, frontend to port 3000, and database to port 5432. Pass the 'DATABASE_URL' and 'OPENAI_API_KEY' securely as environment variables.

Include a complete production-grade .gitignore file in the root directory that excludes node_modules, python virtual environments, local environment files, .DS_Store, and docker volume folders to ensure the repository is ready to push cleanly to GitHub.

Change the AI integration from OpenAI to a 100% free local Ollama setup. Use 'nomic-embed-text' (768 dimensions) for embeddings and 'llama3' for streaming chat completions. Update the PostgreSQL init.sql schema to vector(768). Add the ollama service container to docker-compose.yml.

