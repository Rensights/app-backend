# Quick Fix: Microsoft Graph Credentials Not Working

## Immediate Solution

Run these commands to create the secret and restart the pods:

### For Dev Environment:

```bash
# 1. Create the secret (replace <YOUR_CLIENT_SECRET> with actual secret value)
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id='e84f3a33-9e68-4a68-aae3-bebba66459e6' \
  --from-literal=client-id='70a6e6ef-c980-47dc-89ea-70ebd47fd56d' \
  --from-literal=client-secret='<YOUR_CLIENT_SECRET>' \
  --namespace=dev \
  --dry-run=client -o yaml | kubectl apply -f -

# 2. Restart the deployment
kubectl rollout restart deployment backend -n dev

# 3. Wait for rollout
kubectl rollout status deployment backend -n dev --timeout=5m

# 4. Verify it's working
kubectl logs -n dev -l app.kubernetes.io/name=backend --tail=50 | grep -i "microsoft graph"
```

### For Prod Environment:

```bash
# 1. Create the secret (replace <YOUR_CLIENT_SECRET> with actual secret value)
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id='e84f3a33-9e68-4a68-aae3-bebba66459e6' \
  --from-literal=client-id='70a6e6ef-c980-47dc-89ea-70ebd47fd56d' \
  --from-literal=client-secret='<YOUR_CLIENT_SECRET>' \
  --namespace=prod \
  --dry-run=client -o yaml | kubectl apply -f -

# 2. Restart the deployment
kubectl rollout restart deployment backend -n prod

# 3. Wait for rollout
kubectl rollout status deployment backend -n prod --timeout=5m

# 4. Verify it's working
kubectl logs -n prod -l app.kubernetes.io/name=backend --tail=50 | grep -i "microsoft graph"
```

## Why This Happens

The Helm chart is configured correctly, but:
1. The Kubernetes secret doesn't exist yet (CI/CD might not have created it)
2. Even if the secret exists, pods need to be restarted to load it
3. The GitHub secret `MICROSOFT_CLIENT_SECRET` might not be set in your repository

## After Running the Commands

You should see in the logs:
- ✅ `Microsoft Graph client initialized successfully`
- ❌ NOT: `Microsoft Graph credentials are not configured!`

## Next Steps

1. **Set GitHub Secret** (so future deployments work automatically):
   - Go to: https://github.com/Rensights/app-backend/settings/secrets/actions
   - Add secret: `MICROSOFT_CLIENT_SECRET` = Get the value from Azure Portal → App Registrations → Your App → Certificates & secrets → Client secrets → Copy the **Value**

2. **Verify Secret Exists**:
   ```bash
   kubectl get secret microsoft-graph-credentials -n dev
   kubectl describe secret microsoft-graph-credentials -n dev
   ```

3. **Check Environment Variables in Pod**:
   ```bash
   POD=$(kubectl get pods -n dev -l app.kubernetes.io/name=backend -o jsonpath='{.items[0].metadata.name}')
   kubectl exec -it $POD -n dev -- env | grep MICROSOFT
   ```

