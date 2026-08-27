# Kubernetes manifests

Deploys the backend (`Deployment` + `Service`), its config (`ConfigMap`), and its
secrets (`Secret`, applied from a local copy you create — never committed).
A convenience Postgres `Deployment`/`Service`/`PVC` is included for local/demo
clusters; point `DB_URL` at a managed database instead for anything real.

## Apply order

```bash
# 1. Build and load the image (adjust for your cluster: kind, minikube, a registry, etc.)
docker build -t file-storage-backend:latest ../backend
kind load docker-image file-storage-backend:latest   # only if using kind

# 2. Secrets (never commit the real file)
cp secret.example.yaml secret.yaml
# edit secret.yaml with real values
kubectl apply -f secret.yaml

# 3. Everything else
kubectl apply -f configmap.yaml
kubectl apply -f postgres.yaml
kubectl apply -f uploads-pvc.yaml
kubectl apply -f deployment.yaml
kubectl apply -f service.yaml
```

## Notes

- `deployment.yaml` pulls all env vars from the `ConfigMap` + `Secret` via `envFrom`,
  matching the `${VAR:default}` placeholders in `application.properties`.
- Readiness/liveness probes hit `/actuator/health`, which is `permitAll` in
  `SecurityConfig` so the kubelet doesn't need a token.
- `file-storage-uploads-pvc` persists uploaded binaries across pod restarts;
  with `replicas: 2` and `ReadWriteOnce` this only works on a single node — use
  `ReadWriteMany` (or move storage to S3/GCS) before scaling across nodes for real.
