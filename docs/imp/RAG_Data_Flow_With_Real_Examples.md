# RAG Pipeline — Real Data at Every Step
## Exactly What Goes In and What Comes Out

This document shows the ACTUAL data format at each stage — from PDF upload to AI answer. Follow it like a movie, frame by frame.

---

# PART 1: DOCUMENT UPLOAD & INDEXING

## Scenario
You upload a PDF contract named `service-agreement.pdf`.

---

## STEP 1: User Selects File (Frontend)

**What happens**: User clicks "Choose PDF File" and picks `service-agreement.pdf`

**Input**: A binary PDF file (raw bytes)

**Data being sent** (HTTP request):
```
POST http://34.70.230.73:8000/api/upload
Content-Type: multipart/form-data; boundary=----WebKitFormBoundary

------WebKitFormBoundary
Content-Disposition: form-data; name="file"; filename="service-agreement.pdf"
Content-Type: application/pdf

%PDF-1.4
%âãÏÓ
1 0 obj
<< /Type /Catalog /Pages 2 0 R >>
... (binary PDF bytes) ...
------WebKitFormBoundary--
```

---

## STEP 2: Backend Receives File (UploadController)

**Input**: `FilePart` object (reactive wrapper around the uploaded file)

**Code**:
```java
@PostMapping("/upload")
public Mono<Map<String, Object>> uploadDocument(@RequestPart("file") FilePart filePart) {
    return Mono.fromCallable(() -> ingestionService.ingestDocument(filePart))
```

**What we have now**: A reference to the uploaded file, filename = `"service-agreement.pdf"`

---

## STEP 3: Save to Temp File & Extract Text (Tika)

**Input**: The PDF binary

**Code**:
```java
TikaDocumentReader reader = new TikaDocumentReader(new FileSystemResource(file));
List<Document> documents = reader.get();
```

**What Tika does**: Opens the PDF, extracts ALL text (ignoring images/formatting)

**OUTPUT — Raw extracted text** (one big `Document` object):
```
Document {
  content: "SERVICE AGREEMENT

  This Service Agreement (\"Agreement\") is entered into as of January 1, 2025,
  by and between Acme Corporation (\"Client\") and TechServ Solutions (\"Provider\").

  1. PAYMENT TERMS
  The Client shall pay the Provider a monthly fee of $5,000, due within 30 days
  of the invoice date. Late payments incur a 2% monthly interest charge.

  2. TERM AND TERMINATION
  This Agreement shall remain in effect for 12 months. Either party may terminate
  with 60 days written notice.

  3. CONFIDENTIALITY
  Both parties agree to keep all shared information confidential for 5 years...

  ... (5000 more characters of contract text) ...",
  metadata: { source: "service-agreement.pdf" }
}
```

**Key point**: At this stage it's ONE giant blob of text (could be 10,000+ characters).

---

## STEP 4: Chunking (TokenTextSplitter)

**Input**: The single large Document (10,000 characters)

**Code**:
```java
TokenTextSplitter splitter = new TokenTextSplitter(1000, 200, 5, 10000, true);
List<Document> chunks = splitter.apply(documents);
```

**What it does**: Breaks the big text into smaller overlapping pieces (~1000 tokens each, with 200 token overlap between consecutive chunks).

**Why chunk?** 
- AI models have input size limits
- Smaller chunks = more precise similarity matches
- Overlap ensures a sentence split across chunks isn't lost

**OUTPUT — A list of smaller Document chunks**:
```
chunks = [
  Document {
    id: "chunk-0",
    content: "SERVICE AGREEMENT

    This Service Agreement (\"Agreement\") is entered into as of January 1, 2025,
    by and between Acme Corporation (\"Client\") and TechServ Solutions (\"Provider\").

    1. PAYMENT TERMS
    The Client shall pay the Provider a monthly fee of $5,000, due within 30 days
    of the invoice date. Late payments incur a 2% monthly interest charge.",
    metadata: { source: "service-agreement.pdf" }
  },

  Document {
    id: "chunk-1",
    content: "of the invoice date. Late payments incur a 2% monthly interest charge.

    2. TERM AND TERMINATION
    This Agreement shall remain in effect for 12 months. Either party may terminate
    with 60 days written notice.",
    metadata: { source: "service-agreement.pdf" }
  },

  Document {
    id: "chunk-2",
    content: "with 60 days written notice.

    3. CONFIDENTIALITY
    Both parties agree to keep all shared information confidential for 5 years...",
    metadata: { source: "service-agreement.pdf" }
  }
  ... more chunks ...
]
```

**Notice the overlap**: chunk-0 ends with "...interest charge." and chunk-1 begins with "of the invoice date. Late payments incur a 2% monthly interest charge." — that repeated text is the 200-token overlap.

---

## STEP 5: Enrich Metadata

**Input**: The chunks from Step 4

**Code**:
```java
for (Document chunk : chunks) {
    chunk.getMetadata().put("contract_id", 1L);
    chunk.getMetadata().put("user_id", 101L);
}
```

**OUTPUT — Each chunk now tagged with owner info**:
```
Document {
  id: "chunk-0",
  content: "SERVICE AGREEMENT ... monthly fee of $5,000 ...",
  metadata: {
    source: "service-agreement.pdf",
    contract_id: 1,        ← ADDED
    user_id: 101           ← ADDED
  }
}
```

**Why?** So later, when User 101 asks a question, we only search THEIR contract's chunks (multi-tenant isolation).

---

## STEP 6: Delete Old Vectors

**Code**:
```java
jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'contract_id' = ?", "1");
```

**SQL executed**:
```sql
DELETE FROM vector_store WHERE metadata->>'contract_id' = '1';
```

**Effect**: Removes any previously uploaded contract's chunks (replace, not append).

---

## STEP 7: Embedding (The Magic — Text → Numbers)

**Input**: Each chunk's text

**Code**:
```java
vectorStore.add(chunks);  // This ONE line triggers embedding + storage
```

**What happens internally for EACH chunk**:

### 7a. Send chunk text to Ollama embedding model

**Request to Ollama** (`nomic-embed-text`):
```json
POST http://ollama:11434/api/embeddings
{
  "model": "nomic-embed-text",
  "prompt": "SERVICE AGREEMENT This Service Agreement... monthly fee of $5,000, due within 30 days..."
}
```

### 7b. Ollama returns a vector (768 numbers)

**Response from Ollama**:
```json
{
  "embedding": [
    0.023451, -0.118234, 0.087123, 0.234567, -0.045678,
    0.156789, -0.098234, 0.012345, 0.198765, -0.076543,
    ... (758 more floating-point numbers) ...
    0.034521, -0.087654, 0.145632, 0.098721, -0.023456
  ]
}
```

**This is the embedding** — 768 numbers that represent the MEANING of the text.

### What does the vector actually mean?

Think of it as coordinates in 768-dimensional space. Texts with similar meaning end up close together:

```
"monthly fee of $5,000"        → [0.02, -0.11, 0.08, ...]
"payment amount per month"     → [0.03, -0.10, 0.09, ...]  ← VERY CLOSE (similar meaning)
"confidentiality for 5 years"  → [0.87, 0.34, -0.12, ...]  ← FAR AWAY (different meaning)
```

---

## STEP 8: Store in PostgreSQL (pgvector)

**Input**: chunk text + metadata + the 768-number vector

**SQL executed** (for each chunk):
```sql
INSERT INTO vector_store (id, content, metadata, embedding)
VALUES (
  'a1b2c3d4-e5f6-7890-abcd-ef1234567890',           -- random UUID
  'SERVICE AGREEMENT ... monthly fee of $5,000 ...', -- the text
  '{"source":"service-agreement.pdf","contract_id":1,"user_id":101}',  -- JSONB metadata
  '[0.023451,-0.118234,0.087123,...768 numbers...]'  -- the vector
);
```

**What the database table looks like AFTER upload**:

| id | content | metadata | embedding |
|----|---------|----------|-----------|
| a1b2... | "SERVICE AGREEMENT...$5,000..." | {"contract_id":1,"user_id":101} | [0.023, -0.118, 0.087, ...] |
| b2c3... | "...2% interest...60 days notice..." | {"contract_id":1,"user_id":101} | [0.045, -0.092, 0.134, ...] |
| c3d4... | "...CONFIDENTIALITY...5 years..." | {"contract_id":1,"user_id":101} | [0.871, 0.334, -0.121, ...] |

---

## STEP 9: Return Success to Frontend

**OUTPUT — JSON response**:
```json
{
  "status": "success",
  "filename": "service-agreement.pdf",
  "chunks": 8,
  "message": "Document processed and indexed successfully",
  "auditStatus": "Audit logged: service-agreement.pdf"
}
```

**Frontend shows**: `✓ "service-agreement.pdf" processed — 8 chunks indexed`

---

# PART 2: ASKING A QUESTION (RAG QUERY)

## Scenario
User types: **"What are the payment terms?"**

---

## STEP 1: Frontend Sends Question

**OUTPUT — HTTP request**:
```json
POST http://34.70.230.73:8000/api/chat/stream
Content-Type: application/json

{
  "contract_id": 1,
  "user_id": 101,
  "question": "What are the payment terms?"
}
```

---

## STEP 2: Rate Limit Check (Redis)

**Code**:
```java
rateLimiterService.isAllowed(101L)
```

**Redis command**:
```
INCR ratelimit:101      → returns 3 (3rd request this minute)
```

**Decision**: 3 ≤ 20 → ALLOWED, continue.

---

## STEP 3: Cache Check (Redis)

**Code**:
```java
String cacheKey = cacheService.generateCacheKey(1L, 101L, "What are the payment terms?");
```

**Cache key generated**:
```
rag:response:1:101:8f3a2b1c9d4e5f67
                    └─ SHA-256 hash of "what are the payment terms?" (lowercased)
```

**Redis command**:
```
GET rag:response:1:101:8f3a2b1c9d4e5f67
```

**Two outcomes**:
- **Cache HIT** → returns stored answer instantly (skip to end)
- **Cache MISS** (first time) → returns nothing → run full RAG pipeline ↓

---

## STEP 4: Embed the Question (Ollama)

**Input**: `"What are the payment terms?"`

**Request to Ollama**:
```json
POST http://ollama:11434/api/embeddings
{
  "model": "nomic-embed-text",
  "prompt": "What are the payment terms?"
}
```

**OUTPUT — Question vector (768 numbers)**:
```
[0.021, -0.115, 0.091, 0.230, -0.041, ... 763 more ...]
```

Notice this is CLOSE to the vector of the chunk containing "monthly fee of $5,000, due within 30 days" because they have similar meaning.

---

## STEP 5: Vector Similarity Search (PostgreSQL)

**Code**:
```java
SearchRequest searchRequest = SearchRequest.query("What are the payment terms?")
    .withTopK(3)
    .withFilterExpression(contract_id=1 AND user_id=101);
List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);
```

**SQL executed**:
```sql
SELECT content, metadata,
       embedding <=> '[0.021,-0.115,0.091,...]' AS distance
FROM vector_store
WHERE metadata->>'contract_id' = '1'
  AND metadata->>'user_id' = '101'
ORDER BY distance ASC       -- closest vectors first (cosine distance)
LIMIT 3;                    -- top 3 matches
```

**What `<=>` means**: pgvector's cosine distance operator. Lower = more similar.

**OUTPUT — Top 3 most relevant chunks**:
```
[
  Document {
    content: "1. PAYMENT TERMS. The Client shall pay the Provider a monthly fee
              of $5,000, due within 30 days of the invoice date. Late payments
              incur a 2% monthly interest charge.",
    distance: 0.12    ← very close match!
  },
  Document {
    content: "of the invoice date. Late payments incur a 2% monthly interest charge.
              2. TERM AND TERMINATION...",
    distance: 0.28
  },
  Document {
    content: "5. INVOICING. Invoices will be sent electronically on the 1st...",
    distance: 0.35
  }
]
```

---

## STEP 6: Build Context String

**Code**:
```java
String context = relevantDocs.stream()
    .map(Document::getContent)
    .collect(Collectors.joining("\n\n---\n\n"));
```

**OUTPUT — Combined context**:
```
1. PAYMENT TERMS. The Client shall pay the Provider a monthly fee of $5,000,
due within 30 days of the invoice date. Late payments incur a 2% monthly
interest charge.

---

of the invoice date. Late payments incur a 2% monthly interest charge.
2. TERM AND TERMINATION...

---

5. INVOICING. Invoices will be sent electronically on the 1st...
```

---

## STEP 7: Build the Full Prompt

**Code**:
```java
String systemPrompt = SYSTEM_PROMPT_TEMPLATE.replace("{context}", context);
```

**OUTPUT — The complete prompt sent to Llama3**:
```
=== SYSTEM MESSAGE ===
You are a helpful AI contract analyst. Your role is to answer questions
about contract documents accurately.

STRICT RULES:
1. Only answer based on the provided context below.
2. If the context does not contain enough information to answer the question,
   respond EXACTLY with: "I cannot find that information in the contract."
3. Do not make assumptions or infer information not present in the context.
4. Be precise and cite specific clauses or sections when possible.

CONTEXT FROM CONTRACT:
1. PAYMENT TERMS. The Client shall pay the Provider a monthly fee of $5,000,
due within 30 days of the invoice date. Late payments incur a 2% monthly
interest charge.
---
of the invoice date. Late payments incur a 2% monthly interest charge...
---
5. INVOICING. Invoices will be sent electronically on the 1st...

=== USER MESSAGE ===
What are the payment terms?
```

---

## STEP 8: LLM Generates Answer (Ollama Llama3 — Streaming)

**Code**:
```java
chatClient.prompt()
    .system(systemPrompt)
    .user("What are the payment terms?")
    .stream()
    .content();
```

**Request to Ollama**:
```json
POST http://ollama:11434/api/chat
{
  "model": "llama3",
  "messages": [
    {"role": "system", "content": "You are a helpful AI contract analyst...CONTEXT: ...$5,000..."},
    {"role": "user", "content": "What are the payment terms?"}
  ],
  "stream": true
}
```

**OUTPUT — Ollama streams tokens one by one**:
```
{"message":{"content":"The"}}
{"message":{"content":" payment"}}
{"message":{"content":" terms"}}
{"message":{"content":" are"}}
{"message":{"content":" as"}}
{"message":{"content":" follows"}}
{"message":{"content":":"}}
{"message":{"content":" a"}}
{"message":{"content":" monthly"}}
{"message":{"content":" fee"}}
{"message":{"content":" of"}}
{"message":{"content":" $5,000"}}
... continues ...
```

---

## STEP 9: Stream to Frontend (SSE)

**OUTPUT — Server-Sent Events to browser**:
```
data:The
data: payment
data: terms
data: are
data: as
data: follows
data::
data: a
data: monthly
data: fee
data: of
data: $5,000
...
```

**What the user sees** (building up in real-time):
```
T
The
The payment
The payment terms
The payment terms are
The payment terms are as follows: a monthly fee of $5,000, due within 30 days
of the invoice date. Late payments incur a 2% monthly interest charge.
```

---

## STEP 10: Cache & Save After Completion

**Code** (runs after last token):
```java
.doOnComplete(() -> {
    cacheService.cacheResponse(cacheKey, fullResponse).subscribe();
    chatHistoryService.saveMessage(1L, 101L, "user", question).subscribe();
    chatHistoryService.saveMessage(1L, 101L, "assistant", fullResponse).subscribe();
    auditKafkaProducer.sendAuditEvent(...);
});
```

**Redis stores**:
```
SET rag:response:1:101:8f3a2b1c9d4e5f67
    "The payment terms are as follows: a monthly fee of $5,000..."
    EX 3600      (expires in 1 hour)

RPUSH chat:history:1:101 '{"role":"user","content":"What are the payment terms?"}'
RPUSH chat:history:1:101 '{"role":"assistant","content":"The payment terms are..."}'
```

**Next time someone asks the same question** → Step 3 cache HIT → instant answer (2ms instead of 5-10 seconds).

---

# THE COMPLETE PICTURE (One Glance)

```
UPLOAD:
  PDF file
    → Tika extracts text        → "SERVICE AGREEMENT... $5,000..."  (10,000 chars)
    → TokenTextSplitter chunks   → 8 chunks of ~1000 chars each
    → Add metadata               → each chunk tagged {contract_id:1, user_id:101}
    → Ollama embeds each chunk   → each chunk → [768 numbers]
    → Store in PostgreSQL        → rows of (text + metadata + vector)

QUERY:
  "What are the payment terms?"
    → Ollama embeds question     → [768 numbers]
    → PostgreSQL finds 3 closest → chunks about payment
    → Build context              → combined text of 3 chunks
    → Build prompt               → system rules + context + question
    → Ollama Llama3 generates    → streams tokens
    → SSE to browser             → "The payment terms are... $5,000..."
    → Cache + history in Redis   → for next time
```

---

# KEY MENTAL MODELS

| Concept | Think of it as... |
|---------|-------------------|
| **Embedding** | Converting text into GPS coordinates of "meaning" (768 dimensions) |
| **Chunk** | A paragraph-sized piece of the document |
| **Vector** | A list of 768 numbers representing meaning |
| **Similarity search** | Finding the paragraphs whose "meaning coordinates" are closest to the question's coordinates |
| **Context** | The relevant paragraphs handed to the AI as reference material |
| **RAG** | "Here are the relevant pages, now answer using ONLY these" |
| **Token** | A word or word-piece the AI generates one at a time |
| **Streaming** | Sending each word as it's generated, not waiting for the full answer |

---

# DEEP DIVE: What Does "768-Dimension Embedding" Really Mean?

## Start Simple: 2 Dimensions

Imagine you rate every food on just **2 things**:
- Dimension 1: How **sweet** is it? (0 to 1)
- Dimension 2: How **spicy** is it? (0 to 1)

```
Ice cream    → [0.9, 0.0]   (very sweet, not spicy)
Chocolate    → [0.8, 0.0]   (sweet, not spicy)
Chili pepper → [0.0, 0.9]   (not sweet, very spicy)
Hot sauce    → [0.1, 0.8]   (barely sweet, spicy)
```

Plot them on a graph:

```
sweet
 1.0 │ Ice cream ● 
     │ Chocolate ●
     │
 0.5 │
     │              ● Hot sauce
     │              ● Chili pepper
 0.0 └────────────────────────── spicy
     0.0          0.5         1.0
```

**Notice**: Ice cream and Chocolate are CLOSE (both sweet). Chili and Hot sauce are CLOSE (both spicy). Ice cream is FAR from Chili.

**That closeness = similarity.** With just 2 numbers, we captured "meaning" (taste).

---

## Now Scale Up to 768

Text meaning is far more complex than food taste. You can't capture "meaning" with just 2 numbers. So the embedding model uses **768 numbers** — 768 different "dimensions of meaning."

Each number captures some subtle aspect of meaning. You don't know exactly what each one represents (the AI learned them during training), but conceptually:

```
Dimension 1   → maybe "is this about money?"
Dimension 2   → maybe "is this about time/dates?"
Dimension 3   → maybe "is this formal/legal language?"
Dimension 4   → maybe "is this about people/parties?"
...
Dimension 768 → maybe "is this a question or a statement?"
```

So a sentence about payment becomes:
```
"monthly fee of $5,000"  →  [0.9, 0.3, 0.8, 0.2, ... 764 more ...]
                              ↑    ↑    ↑    ↑
                          money time legal people
                          HIGH  med  HIGH  low
```

---

## Why This Enables "Smart" Search

```
Question: "What are the payment terms?"
  → embeds to → [0.9, 0.3, 0.7, 0.2, ...]   (HIGH on money dimension)

Chunk A: "monthly fee of $5,000"
  → embeds to → [0.9, 0.3, 0.8, 0.2, ...]   (HIGH on money) ← CLOSE! ✅

Chunk B: "confidentiality for 5 years"
  → embeds to → [0.1, 0.6, 0.7, 0.3, ...]   (LOW on money) ← FAR ✗
```

Even though the question says "payment" and the chunk says "fee" (DIFFERENT words!), their 768-number fingerprints are close because they MEAN similar things. That's why it's called **semantic** (meaning-based) search, not keyword search.

---

## The Physical Analogy

```
2 dimensions   = a point on a piece of paper (x, y)
3 dimensions   = a point in a room (x, y, z) — length, width, height
768 dimensions = a point in a space with 768 axes
```

You can't *visualize* 768 dimensions, but the math handles it fine. The distance formula works the same whether it's 2 numbers or 768 numbers.

---

## Why Exactly 768?

It's simply the output size the `nomic-embed-text` model was designed with. Different models produce different sizes:

| Model | Dimensions |
|-------|-----------|
| nomic-embed-text (ours) | 768 |
| OpenAI text-embedding-3-small | 1536 |
| OpenAI text-embedding-3-large | 3072 |

More dimensions = more nuance captured, but more storage and compute. 768 is a good balance for accuracy vs cost.

**This is why our database column is `vector(768)`** — it must EXACTLY match the model's output size. If we switched to a model outputting 1536 numbers, we'd change the column to `vector(1536)`.

---

## How Similarity is Measured (Cosine Similarity)

Once both the question and chunks are vectors, we measure the ANGLE between them:

```
Small angle  = pointing the same direction = similar meaning = HIGH similarity
Large angle  = pointing different directions = different meaning = LOW similarity
```

```
        Chunk A (payment)
          ↗
         ╱  small angle = similar
        ╱
Question ────→ 
        ╲
         ╲  large angle = different
          ↘
        Chunk B (confidentiality)
```

Cosine similarity ranges from -1 to 1:
- **1.0** = identical direction (same meaning)
- **0.0** = perpendicular (unrelated)
- **-1.0** = opposite direction

In our HNSW index, PostgreSQL uses `vector_cosine_ops` to do exactly this comparison — extremely fast, even across millions of vectors.

---

## One-Line Summary

> **768 dimensions** = the model describes each piece of text using 768 different "meaning scores." Texts with similar scores mean similar things, so we find relevant content by finding the closest number-fingerprints — not by matching exact words.

---

*End of Document*
