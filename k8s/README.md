# Kubernetes Deployment — AI Contract Analyzer

## Architecture Overview

```
                         ┌─────────────────────────────────┐
                         │          INTERNET                │
                         └────────────────┬────────────────┘
                                          │
                         ┌────────────────▼────────────────┐
                         │     Ingress Controller (nginx)   │
                         │     Routes by URL path           │
                         └──────┬─────────────────┬────────┘
                                │                 │
                    /api/*      │                 │    /*
                                │                 │
               ┌────────────────▼───┐   ┌───────▼────────────────┐
               │  backend-service    │   │  frontend-service       │
               │  (Load Balancer)    │   │  (Load Balancer)        │
               └──┬──────┬──────┬──┘   └──┬──────────────┬──────┘
                  │      │      │          │              │
              ┌───▼─┐┌───▼─┐┌──▼──┐   ┌──▼───┐      ┌──▼───┐
              │Pod 1││Pod 2││Pod 3│   │Pod 1 │      │Pod 2 │
              │Back ││Back ││Back │   │Front │      │Front │
              └─────┘└─────┘└─────┘   └──────┘      └──────┘
                  │                        
                  │ connects to
                  │
     ┌────────────▼──────────┐   ┌──────────────────────┐
     │  postgres-service      │   │  ollama-service       │
     │  (StatefulSet)         │   │  (Deployment)         │
     │  1 replica + PVC       │   │  1 replica + PVC      │
     └────────────────────────┘   └──────────────────────┘
```

## Files Explained

| File | Purpose |
|------|---------|
| `namespace.yaml` | Creates isolated namespace `contract-analyzer` |
| `secrets.yaml` | Stores database credentials (base64 encoded) |
| `configmap.yaml` | Stores app configuration (connection strings, model names) |
| `postgres.yaml` | Database: StatefulSet + PVC + init SQL + Service |
| `ollama.yaml` | AI models: Deployment + PVC + init container + Service |
| `backend.yaml` | Spring Boot API: Deployment (2 replicas) + Service |
| `frontend.yaml` | React app: Deployment (2 replicas) + Service |
| `ingress.yaml` | External routing: `/api/*` → backend, `/*` → frontend |
| `hpa.yaml` | Auto-scaling: scales pods based on CPU/memory usage |

## Deployment Order

Resources must be applied in dependency order:

```bash
# 1. Create namespace (isolates everything)
kubectl apply -f namespace.yaml

# 2. Create secrets and config (needed by other resources)
kubectl apply -f secrets.yaml
kubectl apply -f configmap.yaml

# 3. Start database (backend depends on it)
kubectl apply -f postgres.yaml

# 4. Start Ollama (backend depends on it)
kubectl apply -f ollama.yaml

# 5. Wait for DB and Ollama to be ready
kubectl wait --for=condition=ready pod -l app=postgres -n contract-analyzer --timeout=120s
kubectl wait --for=condition=ready pod -l app=ollama -n contract-analyzer --timeout=300s

# 6. Start backend
kubectl apply -f backend.yaml

# 7. Start frontend
kubectl apply -f frontend.yaml

# 8. Create ingress (external access)
kubectl apply -f ingress.yaml

# 9. Enable auto-scaling
kubectl apply -f hpa.yaml
```

## Or apply everything at once:

```bash
kubectl apply -f k8s/
```

## Useful Commands

```bash
# Check all pods status
kubectl get pods -n contract-analyzer

# Check services
kubectl get svc -n contract-analyzer

# View logs of a specific pod
kubectl logs -f deployment/backend -n contract-analyzer

# Scale manually
kubectl scale deployment backend --replicas=5 -n contract-analyzer

# Check HPA status (see current replicas vs desired)
kubectl get hpa -n contract-analyzer

# Describe a pod (troubleshooting)
kubectl describe pod <pod-name> -n contract-analyzer

# Port-forward for local testing (no ingress needed)
kubectl port-forward svc/frontend-service 3000:80 -n contract-analyzer
kubectl port-forward svc/backend-service 8000:8080 -n contract-analyzer

# Rolling restart (redeploy without changing image)
kubectl rollout restart deployment/backend -n contract-analyzer

# Check rollout status
kubectl rollout status deployment/backend -n contract-analyzer

# Rollback to previous version
kubectl rollout undo deployment/backend -n contract-analyzer
```

## Docker Compose vs Kubernetes Comparison

| Aspect | Docker Compose | Kubernetes (This Setup) |
|--------|---------------|------------------------|
| Scaling | Manual, single instance | Auto-scaling 2-10 pods |
| High Availability | None (single point of failure) | Multiple replicas across nodes |
| Self-Healing | No auto-restart on crash | Auto-restarts failed pods |
| Load Balancing | None | Built-in Service load balancing |
| Zero-Downtime Deploy | No (gap during restart) | Rolling updates |
| Config Management | .env files | ConfigMaps + Secrets |
| Storage | Docker volumes (single host) | PVCs (survives pod moves) |
| Networking | Docker bridge network | K8s DNS + Service discovery |
| External Access | Direct port mapping | Ingress with path routing |
| Resource Control | None | CPU/memory requests and limits |

## Key Interview Points

1. **Why StatefulSet for PostgreSQL?** — Needs stable identity, ordered startup, and persistent storage that follows the pod if rescheduled.

2. **Why Deployment for Backend/Frontend?** — Stateless services don't need stable identity. Any replica can handle any request.

3. **Why Init Container for Ollama?** — Models download once during init. If the main container restarts, models are already on the PVC.

4. **Why HPA?** — Auto-scales based on actual load. Saves money during low traffic, handles spikes automatically.

5. **Why Ingress?** — Single entry point with URL-based routing. Eliminates need to expose multiple ports. Supports TLS/SSL termination.

6. **Why Resource Limits?** — Prevents one pod from consuming all node resources. Enables fair scheduling across pods.

7. **Why Probes?** — Readiness: don't send traffic to starting pods. Liveness: restart hung pods automatically.
