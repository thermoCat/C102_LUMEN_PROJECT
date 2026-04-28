# SmartCane Backend on k3s

이 디렉터리는 SmartCane backend를 k3s 클러스터에 배포하기 위한 Kubernetes 매니페스트를 담고 있습니다.

## 현재 운영 상태

- backend는 Tailscale 기반 멀티노드 k3s 클러스터에 재배포됨
- 실제로 A와 B 노드에 분산 배치되어 동작 확인
- 외부 health check 응답 확인
- ingress 경로 `/api`, `/map.html`, `/swagger-ui`, `/v3/api-docs` 사용

## 포함 파일

- `namespace.yaml`
- `configmap.yaml`
- `secret.example.yaml`
- `deployment.yaml`
- `service.yaml`
- `middleware.yaml`
- `ingress.yaml`
- `hpa.yaml`
- `pdb.yaml`

## 파일 역할

- `namespace.yaml`
  - `smartcane` 네임스페이스 생성
- `configmap.yaml`
  - backend 런타임 환경변수 정의
- `secret.example.yaml`
  - 실제 secret 작성 예시
- `deployment.yaml`
  - backend pod 실행 정의
- `service.yaml`
  - 클러스터 내부 서비스 정의
- `middleware.yaml`
  - Traefik에서 `/api` prefix 제거
- `ingress.yaml`
  - 외부 도메인 진입점 정의
- `hpa.yaml`
  - HPA 설정
- `pdb.yaml`
  - 배포/장애 상황에서도 최소 pod 수 유지

## 현재 배포 구조

- namespace: `smartcane`
- ingress host: `k14c102.p.ssafy.io`
- service name: `backend-service`
- deployment name: `backend-api`
- node selector:
  - `node.smartcane/role=app`

새 클러스터를 다시 만들면 위 라벨이 사라지므로, 노드에 아래 라벨을 다시 달아야 한다.

```bash
kubectl label node ip-172-26-4-199 node.smartcane/role=app
kubectl label node ip-172-31-45-62 node.smartcane/role=app
kubectl label node ip-172-31-36-235 node.smartcane/role=app
```

## 배포 순서

실제 운영 시에는 A control-plane 노드에서 `kubectl` 로 적용한다.

```bash
kubectl apply -f namespace.yaml
kubectl apply -f secret.yaml
kubectl apply -f configmap.yaml
kubectl apply -f service.yaml
kubectl apply -f deployment.yaml
kubectl apply -f pdb.yaml
kubectl apply -f hpa.yaml
kubectl apply -f middleware.yaml
kubectl apply -f ingress.yaml
```

## 이미지 태그 주의사항

`deployment.yaml` 의 기본 이미지는 placeholder 다.

```yaml
image: change-me/backend-api:latest
```

실제 배포에서는 CI가 이 값을 실제 Docker Hub 이미지 태그로 치환해야 한다.

현재 검증에 사용한 실제 이미지 예시는 아래와 같다.

```text
kjw3568/smartcane-backend:develop-32-ca37e16
```

즉 수동 배포 시에는 `kubectl set image` 또는 CI 렌더링 단계가 반드시 필요하다.

## ingress / API 경로 규칙

- 외부 요청 경로는 `/api/...`
- Traefik middleware가 `/api` 를 제거
- 따라서 Spring Boot controller 는 `/api` 를 중복으로 가지지 않도록 맞춰야 한다

현재 기준 예시:

- hazard API
  - 외부: `/api/hazards`
  - 내부 controller mapping: `/hazards`
- safemap proxy
  - 외부: `/api/proxy/safemap`
  - 내부 controller mapping: `/proxy/safemap`

## 검증 포인트

- pod 배치 확인

```bash
kubectl get pods -n smartcane -o wide
```

- rollout 확인

```bash
kubectl rollout status deployment/backend-api -n smartcane
```

- 외부 health check

```bash
curl http://k14c102.p.ssafy.io/api/actuator/health
```

## 현재 확인된 결과

- backend pod가 A와 B에 실제 분산 배치되어 실행됨
- worker 노드에서 실행된 pod도 readiness 통과
- ingress와 actuator health 응답 확인 완료
