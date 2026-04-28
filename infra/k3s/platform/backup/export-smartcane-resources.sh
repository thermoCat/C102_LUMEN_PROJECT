#!/bin/sh
set -eu

NAMESPACE="${1:-smartcane}"
OUTPUT_DIR="${2:-backup/${NAMESPACE}-$(date +%Y%m%d-%H%M%S)}"

mkdir -p "${OUTPUT_DIR}"

kubectl get \
  configmaps,secrets,services,deployments.apps,ingresses.networking.k8s.io,horizontalpodautoscalers.autoscaling,poddisruptionbudgets.policy,middlewares.traefik.io \
  -n "${NAMESPACE}" -o yaml > "${OUTPUT_DIR}/resources.yaml"
kubectl get pods -n "${NAMESPACE}" -o wide > "${OUTPUT_DIR}/pods.txt"

echo "Backed up ${NAMESPACE} resources into ${OUTPUT_DIR}"
