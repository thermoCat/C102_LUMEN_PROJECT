#!/bin/sh
set -eu

MANIFEST_DIR="${1:-build/deploy/backend}"
KUBECTL_APPLY_ARGS="--validate=false --request-timeout=60s"
K8S_NAMESPACE="${K8S_NAMESPACE:-smartcane}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-300s}"

kubectl apply ${KUBECTL_APPLY_ARGS} -f "${MANIFEST_DIR}/configmap.yaml"
kubectl apply ${KUBECTL_APPLY_ARGS} -f "${MANIFEST_DIR}/service.yaml"
kubectl apply ${KUBECTL_APPLY_ARGS} -f "${MANIFEST_DIR}/deployment.yaml"
kubectl apply ${KUBECTL_APPLY_ARGS} -f "${MANIFEST_DIR}/pdb.yaml"
kubectl apply ${KUBECTL_APPLY_ARGS} -f "${MANIFEST_DIR}/hpa.yaml"
kubectl apply ${KUBECTL_APPLY_ARGS} -f "${MANIFEST_DIR}/middleware.yaml"
kubectl apply ${KUBECTL_APPLY_ARGS} -f "${MANIFEST_DIR}/servers-transport.yaml"
kubectl apply ${KUBECTL_APPLY_ARGS} -f "${MANIFEST_DIR}/ingress.yaml"
kubectl rollout status deployment/backend-api -n "${K8S_NAMESPACE}" --timeout="${ROLLOUT_TIMEOUT}"

echo "Applied backend manifests from ${MANIFEST_DIR}"
