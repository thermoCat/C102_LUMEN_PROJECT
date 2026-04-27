# 모니터링 구성

이 디렉터리는 `kube-prometheus-stack` 기준의 모니터링 설치값을 제공합니다.

설치 목표:

- 클러스터/노드/파드 메트릭 수집
- Grafana 대시보드 제공
- Alertmanager 기반 알림 연동 준비
- ingress 및 리소스 포화도 확인

권장 설치 순서:

```bash
kubectl create namespace monitoring
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f infra/k3s/platform/monitoring/kube-prometheus-stack-values.yaml
```

설치 후 추가 권장 항목:

- backend pod 재시작 알림
- CPU/메모리 사용량 알림
- ingress 5xx 알림
- Jenkins 빌드 실패 알림
