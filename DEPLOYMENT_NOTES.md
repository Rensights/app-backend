# Deployment Notes - Microsoft Graph Credentials

## Summary

Microsoft Graph API credentials are now configured to use Kubernetes secrets instead of hardcoded values in YAML files.

## Changes Made

1. **Helm Chart Updates**:
   - Added `microsoftGraphSecrets: microsoft-graph-credentials` to `charts/values.yaml`
   - Updated `charts/templates/deployment.yaml` to load credentials from Kubernetes secret

2. **Environment Configuration**:
   - Removed hardcoded `MICROSOFT_TENANT_ID`, `MICROSOFT_CLIENT_ID`, and `MICROSOFT_CLIENT_SECRET` from `env-values/dev/backend.yaml` and `env-values/prod/backend.yaml`
   - These values are now loaded from the Kubernetes secret automatically

3. **CI/CD Pipeline**:
   - Added automatic secret creation steps for both dev and prod environments
   - Secrets are created/updated during deployment using GitHub Actions secrets

## Required Actions

### 1. Set GitHub Secret

Add the Microsoft Graph client secret to your GitHub repository secrets:

1. Go to your GitHub repository
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Click **New repository secret**
4. Name: `MICROSOFT_CLIENT_SECRET`
5. Value: Get the client secret value from Azure Portal → App Registrations → Your App → Certificates & secrets → Client secrets → Copy the **Value** (not the Secret ID)
6. Click **Add secret**

### 2. Manual Secret Creation (Alternative)

If you prefer to create the secret manually before deployment:

```bash
# For dev environment
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id='e84f3a33-9e68-4a68-aae3-bebba66459e6' \
  --from-literal=client-id='70a6e6ef-c980-47dc-89ea-70ebd47fd56d' \
  --from-literal=client-secret='<YOUR_CLIENT_SECRET_VALUE>' \
  --namespace=dev

# For prod environment
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id='e84f3a33-9e68-4a68-aae3-bebba66459e6' \
  --from-literal=client-id='70a6e6ef-c980-47dc-89ea-70ebd47fd56d' \
  --from-literal=client-secret='<YOUR_CLIENT_SECRET_VALUE>' \
  --namespace=prod
```

### 3. Deploy

After setting up the GitHub secret or creating the Kubernetes secret manually, push your changes:

```bash
git add .
git commit -m "Configure Microsoft Graph credentials via Kubernetes secrets"
git push origin main
```

The CI/CD pipeline will automatically:
1. Create/update the Kubernetes secret during deployment
2. Deploy the application with the credentials loaded from the secret

## Verification

After deployment, verify the secret is being used:

```bash
# Check if secret exists
kubectl get secret microsoft-graph-credentials -n <namespace>

# Check if pod has the environment variables
kubectl exec -it <pod-name> -n <namespace> -- env | grep MICROSOFT

# Check application logs for Graph API initialization
kubectl logs <pod-name> -n <namespace> | grep -i "microsoft graph"
```

## Troubleshooting

If you see "Microsoft Graph credentials are not configured":

1. **Check secret exists**: `kubectl get secret microsoft-graph-credentials -n <namespace>`
2. **Check secret keys**: `kubectl describe secret microsoft-graph-credentials -n <namespace>`
3. **Restart pods**: `kubectl rollout restart deployment backend -n <namespace>`
4. **Check GitHub secret**: Ensure `MICROSOFT_CLIENT_SECRET` is set in repository secrets

## Security Notes

- ✅ Secrets are stored in Kubernetes (encrypted at rest)
- ✅ Secrets are not committed to Git
- ✅ CI/CD uses GitHub Actions secrets for sensitive values
- ✅ Helm chart only references secret names, not values

