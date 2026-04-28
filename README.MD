# SmartCane 인프라/백엔드 저장소

이 저장소는 SmartCane 프로젝트의 백엔드 애플리케이션과 k3s 기반 인프라 구성을 함께 관리합니다.

현재 포함된 범위:

- Spring Boot 백엔드
- k3s 배포 매니페스트
- Jenkins 기반 CI/CD 파이프라인
- 운영 고도화용 플랫폼 템플릿

주요 경로:

- `backend/`
  - Java/Spring Boot 백엔드
- `infra/k3s/apps/backend/`
  - 백엔드 Kubernetes 매니페스트
- `infra/k3s/cicd/`
  - Jenkins 파이프라인 및 배포 스크립트
- `infra/k3s/platform/`
  - 모니터링, TLS, 백업, GitOps 등 고도화 템플릿
- `infra/aws/`
  - AWS 및 터널링 보조 스크립트

현재 상태:

- GitLab push 기반 Jenkins 자동 실행
- Docker Hub 이미지 빌드/푸시
- k3s 자동 배포
- Traefik ingress 외부 health check 확인 완료 (`k14c102.p.ssafy.io`)
- PostgreSQL PostGIS 활성화 완료

다음 단계:

- 실제 API/비즈니스 로직 구현
- DNS/TLS 정식 연결
- 모니터링, 백업, GitOps 고도화
