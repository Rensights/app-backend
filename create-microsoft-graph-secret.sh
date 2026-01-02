#!/bin/bash

# Script to create Microsoft Graph credentials secret in Kubernetes
# Usage: ./create-microsoft-graph-secret.sh <namespace> [tenant-id] [client-id] [client-secret]

set -e

NAMESPACE=${1:-dev}
TENANT_ID=${2:-"e84f3a33-9e68-4a68-aae3-bebba66459e6"}
CLIENT_ID=${3:-"70a6e6ef-c980-47dc-89ea-70ebd47fd56d"}
CLIENT_SECRET=${4:-""}

if [ -z "$CLIENT_SECRET" ]; then
    echo "❌ Error: Client secret is required!"
    echo "Usage: $0 <namespace> [tenant-id] [client-id] [client-secret]"
    echo "Or set CLIENT_SECRET environment variable"
    exit 1
fi

echo "🔐 Creating Microsoft Graph credentials secret in namespace: ${NAMESPACE}"

# Check if namespace exists
if ! kubectl get namespace ${NAMESPACE} &>/dev/null; then
    echo "❌ Namespace ${NAMESPACE} does not exist. Creating it..."
    kubectl create namespace ${NAMESPACE}
fi

# Delete existing secret if it exists
if kubectl get secret microsoft-graph-credentials -n ${NAMESPACE} &>/dev/null; then
    echo "⚠️  Secret already exists. Deleting it..."
    kubectl delete secret microsoft-graph-credentials -n ${NAMESPACE}
fi

# Create the secret
kubectl create secret generic microsoft-graph-credentials \
  --from-literal=tenant-id="${TENANT_ID}" \
  --from-literal=client-id="${CLIENT_ID}" \
  --from-literal=client-secret="${CLIENT_SECRET}" \
  --namespace=${NAMESPACE}

echo "✅ Secret created successfully!"

# Verify the secret
echo ""
echo "🔍 Verifying secret..."
kubectl get secret microsoft-graph-credentials -n ${NAMESPACE}

echo ""
echo "📋 Secret keys:"
kubectl describe secret microsoft-graph-credentials -n ${NAMESPACE} | grep -E "^Name:|^Type:|^Data:"

echo ""
echo "🔄 Restarting backend deployment to pick up the new secret..."
kubectl rollout restart deployment backend -n ${NAMESPACE} || echo "⚠️  Deployment 'backend' not found. You may need to restart it manually."

echo ""
echo "⏳ Waiting for rollout to complete..."
kubectl rollout status deployment backend -n ${NAMESPACE} --timeout=5m || echo "⚠️  Rollout status check failed or timed out."

echo ""
echo "✅ Done! Check the pod logs to verify Microsoft Graph credentials are loaded:"
echo "   kubectl logs -n ${NAMESPACE} -l app.kubernetes.io/name=backend --tail=50 | grep -i 'microsoft graph'"

