#!/bin/bash
# Script to fix backend Kong configuration issues

set -e

export KUBECONFIG=/root/.kube/config
NAMESPACE="dev"
SERVICE_NAME="backend"

echo "=== Checking backend pod status ==="
kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=$SERVICE_NAME

echo ""
echo "=== Checking backend service ==="
kubectl get svc -n $NAMESPACE -l app.kubernetes.io/name=$SERVICE_NAME

echo ""
echo "=== Checking backend service endpoints ==="
kubectl get endpoints -n $NAMESPACE -l app.kubernetes.io/name=$SERVICE_NAME

echo ""
echo "=== Checking Kong ingress for backend ==="
kubectl get ingress -n $NAMESPACE -l app.kubernetes.io/name=$SERVICE_NAME

echo ""
echo "=== Checking if backend pods are ready ==="
READY_PODS=$(kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=$SERVICE_NAME --field-selector=status.phase=Running -o jsonpath='{.items[*].metadata.name}')
if [ -z "$READY_PODS" ]; then
  echo "❌ No running backend pods found!"
  echo "Checking pod status:"
  kubectl get pods -n $NAMESPACE -l app.kubernetes.io/name=$SERVICE_NAME -o wide
  echo ""
  echo "Recent pod events:"
  kubectl get events -n $NAMESPACE --sort-by='.lastTimestamp' | grep $SERVICE_NAME | tail -10
  exit 1
fi

echo "✅ Found running pods: $READY_PODS"

echo ""
echo "=== Checking service endpoints ==="
ENDPOINTS=$(kubectl get endpoints -n $NAMESPACE -l app.kubernetes.io/name=$SERVICE_NAME -o jsonpath='{.items[0].subsets[0].addresses[*].ip}')
if [ -z "$ENDPOINTS" ]; then
  echo "❌ No endpoints found for backend service!"
  echo "This usually means pods are not ready or service selector doesn't match pod labels"
  exit 1
fi

echo "✅ Service has endpoints: $ENDPOINTS"

echo ""
echo "=== Fixing Kong ingress ==="
INGRESS_NAME=$(kubectl get ingress -n $NAMESPACE -l app.kubernetes.io/name=$SERVICE_NAME -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || echo "")

if [ -n "$INGRESS_NAME" ]; then
  echo "Found ingress: $INGRESS_NAME"
  echo "Deleting existing ingress to recreate..."
  kubectl delete ingress $INGRESS_NAME -n $NAMESPACE || true
  sleep 2
fi

echo "Ingress should be recreated by Helm. If not, you may need to redeploy the backend service."
echo ""
echo "To manually recreate, run:"
echo "kubectl apply -f - <<EOF"
echo "apiVersion: networking.k8s.io/v1"
echo "kind: Ingress"
echo "metadata:"
echo "  name: backend"
echo "  namespace: $NAMESPACE"
echo "  annotations:"
echo "    konghq.com/strip-path: \"false\""
echo "    konghq.com/protocols: \"http\""
echo "    konghq.com/connect-timeout: \"60000\""
echo "    konghq.com/send-timeout: \"60000\""
echo "    konghq.com/read-timeout: \"60000\""
echo "    konghq.com/retries: \"5\""
echo "    konghq.com/preserve-host: \"false\""
echo "spec:"
echo "  ingressClassName: kong"
echo "  rules:"
echo "  - host: dev-api.72.62.40.154.nip.io"
echo "    http:"
echo "      paths:"
echo "      - path: /"
echo "        pathType: Prefix"
echo "        backend:"
echo "          service:"
echo "            name: backend"
echo "            port:"
echo "              number: 8080"
echo "EOF"



