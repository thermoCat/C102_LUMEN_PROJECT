# 모니터링

이 디렉터리는 `kube-prometheus-stack` 기준의 모니터링 설치 파일을 제공합니다.

구성 요소:

- Prometheus
- Grafana
- Alertmanager
- kube-state-metrics
- node-exporter

현재 라우팅 경로:

- Grafana: `http://k14c102.p.ssafy.io/grafana`
- Prometheus: `http://k14c102.p.ssafy.io/prometheus`
- Alertmanager: `http://k14c102.p.ssafy.io/alertmanager`

경로 기반 접근 주의:

- Grafana는 `grafana.ini.server.root_url` 과 `serve_from_sub_path` 를 함께 설정해야 정적 리소스와 로그인 리다이렉트가 정상 동작합니다.
- Prometheus와 Alertmanager도 `externalUrl` 과 `routePrefix` 를 경로에 맞춰 두어야 UI 링크와 리다이렉트가 깨지지 않습니다.

공용 파일과 운영 파일 분리:

- Git에 올리는 공용 템플릿은 [kube-prometheus-stack-values.yaml](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\platform\monitoring\kube-prometheus-stack-values.yaml) 입니다.
- 실제 Grafana 관리자 비밀번호는 Git에 올리지 말고, [kube-prometheus-stack-values.prod.example.yaml](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\platform\monitoring\kube-prometheus-stack-values.prod.example.yaml)을 복사해서 `kube-prometheus-stack-values.prod.yaml` 로 만든 뒤 그 파일에만 넣습니다.
- `kube-prometheus-stack-values.prod.yaml` 은 `.gitignore` 에 등록되어 있어 커밋되지 않습니다.

운영 파일 생성 예시:

```bash
cp infra/k3s/platform/monitoring/kube-prometheus-stack-values.prod.example.yaml \
  infra/k3s/platform/monitoring/kube-prometheus-stack-values.prod.yaml
```

설치 순서:

```bash
kubectl create namespace monitoring
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm upgrade --install kube-prometheus-stack prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  -f infra/k3s/platform/monitoring/kube-prometheus-stack-values.prod.yaml
```

설치 후 확인:

```bash
kubectl get pods -n monitoring
kubectl get ingress -n monitoring
```

추가로 보면 좋은 항목:

- backend pod 상태
- 노드 CPU/메모리 사용량
- ingress 5xx
- Jenkins 상태
