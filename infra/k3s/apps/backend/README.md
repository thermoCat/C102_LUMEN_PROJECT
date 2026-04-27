# SmartCane 백엔드 Kubernetes 매니페스트

이 디렉터리는 SmartCane 백엔드를 k3s 클러스터에 배포하기 위한 매니페스트를 담고 있습니다.

포함 파일:

- `namespace.yaml`
- `configmap.yaml`
- `secret.example.yaml`
- `deployment.yaml`
- `service.yaml`
- `middleware.yaml`
- `ingress.yaml`
- `hpa.yaml`
- `pdb.yaml`

역할 요약:

- `configmap.yaml`
  - 호스트, 포트, 버킷, prefix 같은 비민감 설정값
- `secret.example.yaml`
  - 실제 secret 생성 예시 템플릿
- `deployment.yaml`
  - 백엔드 pod 실행 정의
- `service.yaml`
  - 클러스터 내부 서비스 노출
- `middleware.yaml`
  - Traefik `/api` prefix 제거
- `ingress.yaml`
  - 외부 도메인 라우팅 및 TLS 설정
- `hpa.yaml`
  - CPU/메모리 기준 수평 확장 정책
- `pdb.yaml`
  - 운영 중 최소 가용 pod 보장

배포 흐름:

1. Jenkins가 이미지를 빌드하고 푸시
2. 배포 스크립트가 매니페스트를 렌더링
3. k3s에 ConfigMap, Service, Deployment, HPA, PDB, Middleware, Ingress를 적용

주의:

- 실제 비밀값은 `secret.yaml`로 별도 생성해야 합니다
- TLS는 cert-manager와 DNS가 준비된 뒤 정상 발급됩니다
