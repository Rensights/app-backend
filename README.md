# Backend Service

Main backend API service for Rensights platform.

## Structure

```
backend/
├── src/                    # Source code
│   ├── main/
│   │   ├── java/          # Java source files
│   │   └── resources/     # Application configs
├── Dockerfile              # Container build definition
├── pom.xml                 # Maven project configuration
└── helm/                   # Helm chart for Kubernetes
    ├── Chart.yaml
    ├── values.yaml         # Helm values (use --set for overrides)
    └── templates/          # Kubernetes manifests
```

## Building

```bash
mvn clean package -DskipTests
```

## Docker Build

```bash
docker build -t rensights-backend:latest .
```

## Deployment

Use Helm's standard approach with `values.yaml` and `--set` flags for environment-specific overrides:

### Dev Environment
```bash
helm upgrade --install backend-dev helm/ \
  --set image.tag=dev-latest \
  --set ingress.hosts[0].host=dev-api.72.62.40.154.nip.io \
  -n dev
```

### Production
```bash
helm upgrade --install backend-prod helm/ \
  --set image.tag=latest \
  --set replicaCount=2 \
  --set ingress.hosts[0].host=api.72.62.40.154.nip.io \
  -n prod
```

## Helm Best Practices

- Use `helm/values.yaml` as the base configuration
- Override values at deploy time with `--set` flags
- Or use `--set-file` for complex values
- Or create separate values files and use `-f` flag if needed


Main backend API service for Rensights platform.

## Structure

```
backend/
├── src/                    # Source code
│   ├── main/
│   │   ├── java/          # Java source files
│   │   └── resources/     # Application configs
├── Dockerfile              # Container build definition
├── pom.xml                 # Maven project configuration
└── helm/                   # Helm chart for Kubernetes
    ├── Chart.yaml
    ├── values.yaml         # Helm values (use --set for overrides)
    └── templates/          # Kubernetes manifests
```

## Building

```bash
mvn clean package -DskipTests
```

## Docker Build

```bash
docker build -t rensights-backend:latest .
```

## Deployment

Use Helm's standard approach with `values.yaml` and `--set` flags for environment-specific overrides:

### Dev Environment
```bash
helm upgrade --install backend-dev helm/ \
  --set image.tag=dev-latest \
  --set ingress.hosts[0].host=dev-api.72.62.40.154.nip.io \
  -n dev
```

### Production
```bash
helm upgrade --install backend-prod helm/ \
  --set image.tag=latest \
  --set replicaCount=2 \
  --set ingress.hosts[0].host=api.72.62.40.154.nip.io \
  -n prod
```

## Helm Best Practices

- Use `helm/values.yaml` as the base configuration
- Override values at deploy time with `--set` flags
- Or use `--set-file` for complex values
- Or create separate values files and use `-f` flag if needed