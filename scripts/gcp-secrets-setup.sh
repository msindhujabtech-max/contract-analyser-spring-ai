#!/bin/bash
# ============================================================
# GCP Secret Manager Setup Script
# Run this ONCE to create all secrets in your GCP project
# ============================================================

# Set your GCP project ID
PROJECT_ID="contract-analyser-spring-ai-v1"

echo "=== Setting GCP Project ==="
gcloud config set project $PROJECT_ID

echo "=== Enabling Secret Manager API ==="
gcloud services enable secretmanager.googleapis.com

echo "=== Creating Secrets ==="

# Database secrets
echo -n "jdbc:postgresql://db:5432/contractdb" | \
  gcloud secrets create db-connection-url --data-file=- --replication-policy="automatic"

echo -n "postgres" | \
  gcloud secrets create db-username --data-file=- --replication-policy="automatic"

echo -n "postgres" | \
  gcloud secrets create db-password --data-file=- --replication-policy="automatic"

# Redis secrets
echo -n "redis" | \
  gcloud secrets create redis-host --data-file=- --replication-policy="automatic"

echo -n "" | \
  gcloud secrets create redis-password --data-file=- --replication-policy="automatic"

# Ollama secrets
echo -n "http://ollama:11434" | \
  gcloud secrets create ollama-base-url --data-file=- --replication-policy="automatic"

echo "=== Granting Access to Compute Engine Service Account ==="
# Get the default compute service account
SA_EMAIL=$(gcloud iam service-accounts list --filter="displayName:Compute Engine" --format="value(email)")

# Grant Secret Manager Accessor role
gcloud projects add-iam-policy-binding $PROJECT_ID \
  --member="serviceAccount:$SA_EMAIL" \
  --role="roles/secretmanager.secretAccessor"

echo "=== All secrets created successfully! ==="
echo ""
echo "To verify, run:"
echo "  gcloud secrets list"
echo ""
echo "To view a secret value:"
echo "  gcloud secrets versions access latest --secret=db-password"
echo ""
echo "To update a secret:"
echo "  echo -n 'new-password' | gcloud secrets versions add db-password --data-file=-"
