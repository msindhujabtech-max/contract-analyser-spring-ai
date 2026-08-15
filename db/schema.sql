-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Contracts metadata table
CREATE TABLE IF NOT EXISTS contracts (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    filename VARCHAR(512),
    uploaded_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Vector store table matching Spring AI PgVectorStore schema
CREATE TABLE IF NOT EXISTS vector_store (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    content TEXT NOT NULL,
    metadata JSONB DEFAULT '{}',
    embedding vector(768)
);

-- HNSW index for fast cosine similarity search
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- Index on metadata for tenant isolation queries
CREATE INDEX IF NOT EXISTS vector_store_metadata_idx
    ON vector_store USING gin (metadata jsonb_path_ops);

-- Seed default contract entry for immediate evaluation
INSERT INTO contracts (id, user_id, filename, uploaded_at)
VALUES (1, 101, 'sample-contract.pdf', CURRENT_TIMESTAMP)
ON CONFLICT (id) DO NOTHING;
