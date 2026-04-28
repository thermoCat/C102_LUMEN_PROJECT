# Monitoring

이 디렉터리는 `kube-prometheus-stack` 기반 monitoring 구성을 담고 있습니다.

## 현재 상태

- 예전 클러스터에서는 Grafana, Prometheus, Alertmanager 구성을 사용했음
- 새 Tailscale 기반 클러스터로 재구성한 뒤에는 아직 monitoring 을 다시 설치하지 않음
- 따라서 현재 이 디렉터리는 `재설치용 기준 설정` 으로 봐야 한다

## 포함 파일

- `kube-prometheus-stack-values.yaml`
  - 공용 기본값
- `kube-prometheus-stack-values.prod.example.yaml`
  - 운영용 값 예시
- `README.md`

## 재설치 시 목표 경로

- Grafana: `http://k14c102.p.ssafy.io/grafana`
- Prometheus: `http://k14c102.p.ssafy.io/prometheus`
- Alertmanager: `http://k14c102.p.ssafy.io/alertmanager`

## 운영 파일 생성

```bash
cp infra/k3s/platform/monitoring/kube-prometheus-stack-values.prod.example.yaml \
  infra/k3s/platform/monitoring/kube-prometheus-stack-values.prod.yaml
```

`kube-prometheus-stack-values.prod.yaml` 은 Git에 커밋하지 않는다.

## 설치 순서

```bash
kubectl create namespace monitoring
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f infra/k3s/platform/monitoring/kube-prometheus-stack-values.prod.yaml
```

## 설치 후 확인

```bash
kubectl get pods -n monitoring
kubectl get ingress -n monitoring
```

## 새 클러스터에서 확인하고 싶은 항목

- backend pod 상태
- 노드 CPU / 메모리 사용량
- ingress 5xx 비율
- worker 노드에 분산된 backend pod 상태

## 메모

- 예전에는 worker endpoint 접근 문제 때문에 monitoring ingress가 불안정했다
- 새 클러스터는 Tailscale 기반 data plane 이므로, monitoring 도 이전보다 안정적으로 구성될 가능성이 높다
