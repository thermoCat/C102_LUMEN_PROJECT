# Monitoring

This directory contains the `kube-prometheus-stack` values used for the
SmartCane k3s cluster.

## Access URLs

- Grafana: `https://k14c102.p.ssafy.io/grafana`
- Prometheus: `https://k14c102.p.ssafy.io/prometheus`
- Alertmanager: `https://k14c102.p.ssafy.io/alertmanager`

## What Each Tool Is For

- Grafana
  - Primary operations dashboard
  - Check CPU, memory, pod health, and node-level trends here first
- Prometheus
  - Raw metrics and PromQL queries
  - Use it when you need to confirm that a metric is really being scraped or to test a query directly
- Alertmanager
  - Alert status and incident handling
  - Use it to review firing alerts and to silence noisy alerts during maintenance windows

## Files

- `kube-prometheus-stack-values.yaml`
  - Base shared values
- `kube-prometheus-stack-values.prod.example.yaml`
  - Production example values
- `README.md`

## Production Setup

```bash
cp infra/k3s/platform/monitoring/kube-prometheus-stack-values.prod.example.yaml \
  infra/k3s/platform/monitoring/kube-prometheus-stack-values.prod.yaml
```

Set a real Grafana admin password in `kube-prometheus-stack-values.prod.yaml`
before applying it.

## Install Or Upgrade

```bash
kubectl create namespace monitoring
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f infra/k3s/platform/monitoring/kube-prometheus-stack-values.prod.yaml
```

## Basic Checks

```bash
kubectl get pods -n monitoring
kubectl get ingress -n monitoring
kubectl get certificate -n monitoring
```

## Included Starter Alerts

The values files include a small starter ruleset through
`additionalPrometheusRulesMap`.

- `SmartCaneBackendDeploymentReplicasMismatch`
  - Fires when available backend replicas do not match the desired count for 5 minutes
- `SmartCaneBackendPodsRestarting`
  - Fires when backend containers keep restarting
- `SmartCaneWorkerNodeNotReady`
  - Fires when worker node B or C is NotReady for 5 minutes
- `SmartCaneBackendHighCpu`
  - Fires when backend CPU usage stays above 80 percent of configured limits
- `SmartCaneBackendHighMemory`
  - Fires when backend memory usage stays above 85 percent of configured limits

## Recommended Daily Flow

1. Open Grafana first to see cluster and backend health at a glance.
2. If something looks wrong, inspect the raw metric or PromQL query in Prometheus.
3. If an alert is firing, use Alertmanager to confirm status and silence it during controlled maintenance if needed.
