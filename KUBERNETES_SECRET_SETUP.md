# Kubernetes Secret Setup for Microsoft Graph Credentials

## Overview

Microsoft Graph API credentials are now stored in Kubernetes secrets for security. The deployment automatically loads these credentials from the secret.

## Required Credentials

- **Tenant ID**: `e84f3a33-9e68-4a68-aae3-bebba66459e6`
- **Client ID**: `70a6e6ef-c980-47dc-89ea-70ebd47fd56d`
- **Client Secret**: `<SECRET_VALUE>` (Get from Azure Portal → App Registrations → Your App → Certificates & secrets → Client secrets → Copy the **Value**, not the Secret ID)

## Create the Secret

### For Development Environment

```bash
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id='e84f3a33-9e68-4a68-aae3-bebba66459e6' \
  --from-literal=client-id='70a6e6ef-c980-47dc-89ea-70ebd47fd56d' \
  --from-literal=client-secret='<YOUR_CLIENT_SECRET_VALUE>' \
  --namespace=dev
```

### For Production Environment

```bash
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id='e84f3a33-9e68-4a68-aae3-bebba66459e6' \
  --from-literal=client-id='70a6e6ef-c980-47dc-89ea-70ebd47fd56d' \
  --from-literal=client-secret='<YOUR_CLIENT_SECRET_VALUE>' \
  --namespace=prod
```

## Update Existing Secret

If the secret already exists, you can update it:

```bash
# Delete the old secret (if it exists)
kubectl delete secret microsoft-graph-credentials -n <namespace>

# Create the new secret with all three values
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id='e84f3a33-9e68-4a68-aae3-bebba66459e6' \
  --from-literal=client-id='70a6e6ef-c980-47dc-89ea-70ebd47fd56d' \
  --from-literal=client-secret='<YOUR_CLIENT_SECRET_VALUE>' \
  --namespace=<namespace>
```

## Verify Secret

```bash
# List the secret
kubectl get secret microsoft-graph-credentials -n <namespace>

# Describe the secret (shows keys, not values)
kubectl describe secret microsoft-graph-credentials -n <namespace>

# Decode and view the secret values (for verification only)
kubectl get secret microsoft-graph-credentials -n <namespace> -o jsonpath='{.data.tenant-id}' | base64 -d && echo
kubectl get secret microsoft-graph-credentials -n <namespace> -o jsonpath='{.data.client-id}' | base64 -d && echo
kubectl get secret microsoft-graph-credentials -n <namespace> -o jsonpath='{.data.client-secret}' | base64 -d && echo
```

## How It Works

The Helm chart automatically loads these credentials from the Kubernetes secret:

- `MICROSOFT_TENANT_ID` → `microsoft-graph-credentials.tenant-id`
- `MICROSOFT_CLIENT_ID` → `microsoft-graph-credentials.client-id`
- `MICROSOFT_CLIENT_SECRET` → `microsoft-graph-credentials.client-secret`

These are injected as environment variables in the deployment, which are then read by the Spring Boot application.

## Secret Name Configuration

The secret name is configured in `charts/values.yaml`:

```yaml
secrets:
  microsoftGraphSecrets: microsoft-graph-credentials
```

You can override this in your environment-specific values files if needed.

## Security Note

⚠️ **Never commit secrets to Git!** 

- The secret values are stored securely in Kubernetes
- The Helm chart references the secret name, not the values
- Always use Kubernetes secrets for sensitive credentials in production

## Troubleshooting

If you see errors like "Microsoft Graph credentials are not configured", check:

1. **Secret exists**: `kubectl get secret microsoft-graph-credentials -n <namespace>`
2. **Secret has all keys**: `kubectl describe secret microsoft-graph-credentials -n <namespace>`
3. **Pod is using the secret**: Check pod environment variables:
   ```bash
   kubectl exec -it <pod-name> -n <namespace> -- env | grep MICROSOFT
   ```
4. **Restart pods after creating/updating secret**: 
   ```bash
   kubectl rollout restart deployment <deployment-name> -n <namespace>
   ```

