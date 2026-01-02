# Kubernetes Secret Setup for Microsoft Graph Client Secret

## For Production Environment

For security, store the Microsoft Graph client secret in Kubernetes instead of plain text in YAML files.

### Create the Secret

```bash
# Replace <your-secret-value> with the actual client secret value
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=client-secret='<your-secret-value>' \
  --namespace=<your-namespace>
```

### Update Helm Values

In your `env-values/prod/backend.yaml`, reference the secret:

```yaml
extraEnv:
  MICROSOFT_CLIENT_SECRET:
    valueFrom:
      secretKeyRef:
        name: microsoft-graph-credentials
        key: client-secret
```

### Verify Secret

```bash
kubectl get secret microsoft-graph-credentials -n <your-namespace>
kubectl describe secret microsoft-graph-credentials -n <your-namespace>
```

## Current Configuration

- **Dev**: Secret is in plain text (for development only)
- **Prod**: Should use Kubernetes secret (as shown above)

## Security Note

⚠️ **Never commit secrets to Git!** The current dev configuration has the secret in plain text for testing purposes. For production, always use Kubernetes secrets.

