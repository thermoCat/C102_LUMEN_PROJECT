#!/bin/sh
set -eu

MANIFEST_DIR="${1:-build/deploy/backend}"

kubectl apply -f "${MANIFEST_DIR}/configmap.yaml"
kubectl apply -f "${MANIFEST_DIR}/service.yaml"
kubectl apply -f "${MANIFEST_DIR}/deployment.yaml"
kubectl apply -f "${MANIFEST_DIR}/ingress.yaml"

echo "Applied backend manifests from ${MANIFEST_DIR}"
