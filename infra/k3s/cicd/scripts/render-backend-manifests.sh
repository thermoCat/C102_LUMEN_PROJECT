#!/bin/sh
set -eu

OUTPUT_DIR="${1:-build/deploy/backend}"
NAMESPACE="${K8S_NAMESPACE:-smartcane}"
IMAGE="${BACKEND_IMAGE:-change-me/backend-api:latest}"
INGRESS_HOST="${BACKEND_INGRESS_HOST:-api.smartcane.com}"

SOURCE_DIR="infra/k3s/apps/backend"

mkdir -p "${OUTPUT_DIR}"

cp "${SOURCE_DIR}/namespace.yaml" "${OUTPUT_DIR}/namespace.yaml"
cp "${SOURCE_DIR}/configmap.yaml" "${OUTPUT_DIR}/configmap.yaml"
cp "${SOURCE_DIR}/service.yaml" "${OUTPUT_DIR}/service.yaml"
cp "${SOURCE_DIR}/secret.example.yaml" "${OUTPUT_DIR}/secret.example.yaml"

sed \
  -e "s|namespace: smartcane|namespace: ${NAMESPACE}|g" \
  -e "s|image: change-me/backend-api:latest|image: ${IMAGE}|g" \
  "${SOURCE_DIR}/deployment.yaml" > "${OUTPUT_DIR}/deployment.yaml"

sed \
  -e "s|namespace: smartcane|namespace: ${NAMESPACE}|g" \
  -e "s|host: api.smartcane.com|host: ${INGRESS_HOST}|g" \
  "${SOURCE_DIR}/ingress.yaml" > "${OUTPUT_DIR}/ingress.yaml"

echo "Rendered backend manifests into ${OUTPUT_DIR}"
