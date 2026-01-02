# Troubleshooting Microsoft Graph API Credentials

## Issue: "Microsoft Graph credentials are not configured"

If you're seeing this error after deployment, follow these steps:

## Step 1: Check if Secret Exists

```bash
# For dev environment
kubectl get secret microsoft-graph-credentials -n dev

# For prod environment
kubectl get secret microsoft-graph-credentials -n prod
```

If the secret doesn't exist, you'll see: `Error from server (NotFound): secrets "microsoft-graph-credentials" not found`

## Step 2: Create the Secret Manually

### Option A: Use the Script (Recommended)

```bash
cd app-backend
./create-microsoft-graph-secret.sh dev
# or for prod:
./create-microsoft-graph-secret.sh prod
```

### Option B: Manual kubectl Command

```bash
# For dev
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id='e84f3a33-9e68-4a68-aae3-bebba66459e6' \
  --from-literal=client-id='70a6e6ef-c980-47dc-89ea-70ebd47fd56d' \
  --from-literal=client-secret='<YOUR_CLIENT_SECRET_VALUE>' \
  --namespace=dev

# For prod
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id='e84f3a33-9e68-4a68-aae3-bebba66459e6' \
  --from-literal=client-id='70a6e6ef-c980-47dc-89ea-70ebd47fd56d' \
  --from-literal=client-secret='<YOUR_CLIENT_SECRET_VALUE>' \
  --namespace=prod
```

## Step 3: Verify Secret Contents

```bash
# Check secret exists and has correct keys
kubectl describe secret microsoft-graph-credentials -n dev

# You should see:
# Name:         microsoft-graph-credentials
# Type:         Opaque
# Data
# ====
# client-id:     36 bytes
# client-secret: 44 bytes
# tenant-id:     36 bytes
```

## Step 4: Restart Pods

After creating/updating the secret, restart the deployment:

```bash
# For dev
kubectl rollout restart deployment backend -n dev

# For prod
kubectl rollout restart deployment backend -n prod

# Wait for rollout to complete
kubectl rollout status deployment backend -n dev
```

## Step 5: Verify Environment Variables in Pod

```bash
# Get pod name
POD_NAME=$(kubectl get pods -n dev -l app.kubernetes.io/name=backend -o jsonpath='{.items[0].metadata.name}')

# Check environment variables
kubectl exec -it $POD_NAME -n dev -- env | grep MICROSOFT

# You should see:
# MICROSOFT_TENANT_ID=e84f3a33-9e68-4a68-aae3-bebba66459e6
# MICROSOFT_CLIENT_ID=70a6e6ef-c980-47dc-89ea-70ebd47fd56d
# MICROSOFT_CLIENT_SECRET=<your-secret-value>
```

## Step 6: Check Application Logs

```bash
# Check logs for Graph API initialization
kubectl logs -n dev -l app.kubernetes.io/name=backend --tail=100 | grep -i "microsoft graph"

# You should see:
# Microsoft Graph client initialized successfully
# OR
# Microsoft Graph credentials are not configured!
```

## Common Issues

### Issue 1: Secret exists but pods still show error

**Solution**: Restart the pods. Secrets are only loaded when pods start.

```bash
kubectl rollout restart deployment backend -n dev
```

### Issue 2: Secret keys don't match

The secret must have these exact keys:
- `tenant-id` (not `tenant_id` or `TENANT_ID`)
- `client-id` (not `client_id` or `CLIENT_ID`)
- `client-secret` (not `client_secret` or `CLIENT_SECRET`)

**Solution**: Delete and recreate the secret with correct keys.

### Issue 3: GitHub Secret not set in CI/CD

If the CI/CD workflow didn't create the secret, it means `MICROSOFT_CLIENT_SECRET` GitHub secret is not set.

**Solution**: 
1. Go to GitHub repository → Settings → Secrets and variables → Actions
2. Add `MICROSOFT_CLIENT_SECRET` with value from Azure Portal → App Registrations → Your App → Certificates & secrets → Client secrets → Copy the **Value**
3. Or create the secret manually using the commands above

### Issue 4: Helm chart not referencing the secret

**Solution**: Verify the Helm values reference the secret:

```bash
# Check Helm values
helm get values backend -n dev | grep microsoftGraphSecrets

# Should show:
# microsoftGraphSecrets: microsoft-graph-credentials
```

### Issue 5: Deployment template not loading secret

**Solution**: Verify the deployment template has the environment variables:

```bash
# Check deployment YAML
kubectl get deployment backend -n dev -o yaml | grep -A 5 MICROSOFT

# Should show environment variables with valueFrom.secretKeyRef
```

## Quick Fix Script

Run this to fix everything at once:

```bash
#!/bin/bash
NAMESPACE=${1:-dev}

echo "🔐 Creating secret..."
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id='e84f3a33-9e68-4a68-aae3-bebba66459e6' \
  --from-literal=client-id='70a6e6ef-c980-47dc-89ea-70ebd47fd56d' \
  --from-literal=client-secret='<YOUR_CLIENT_SECRET_VALUE>' \
  --namespace=${NAMESPACE} \
  --dry-run=client -o yaml | kubectl apply -f -

echo "🔄 Restarting deployment..."
kubectl rollout restart deployment backend -n ${NAMESPACE}

echo "⏳ Waiting for rollout..."
kubectl rollout status deployment backend -n ${NAMESPACE} --timeout=5m

echo "✅ Done! Check logs:"
echo "kubectl logs -n ${NAMESPACE} -l app.kubernetes.io/name=backend --tail=50 | grep -i 'microsoft graph'"
```

## Verification Checklist

- [ ] Secret exists: `kubectl get secret microsoft-graph-credentials -n <namespace>`
- [ ] Secret has correct keys: `kubectl describe secret microsoft-graph-credentials -n <namespace>`
- [ ] Pods restarted: `kubectl get pods -n <namespace> -l app.kubernetes.io/name=backend`
- [ ] Environment variables set: `kubectl exec <pod> -n <namespace> -- env | grep MICROSOFT`
- [ ] Application logs show Graph API initialized: `kubectl logs <pod> -n <namespace> | grep -i "microsoft graph"`

