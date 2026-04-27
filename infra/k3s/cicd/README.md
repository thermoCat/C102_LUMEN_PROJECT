# SmartCane Jenkins CI/CD

이 디렉터리는 SmartCane 백엔드를 Jenkins에서 빌드하고 k3s로 배포하기 위한 CI/CD 구성을 담고 있습니다.

구성 요소:

- `Jenkinsfile`
  - 전체 파이프라인 정의
- `jenkins-values.yaml`
  - Jenkins Helm 설치 값
- `rbac/jenkins-smartcane-deployer.yaml`
  - Jenkins 서비스어카운트 배포 권한
- `scripts/render-backend-manifests.sh`
  - 배포용 매니페스트 렌더링
- `scripts/deploy-backend.sh`
  - k3s 적용 스크립트

현재 파이프라인 흐름:

1. GitLab `develop` 브랜치 변경 감지
2. 소스 checkout
3. 테스트 실행
4. bootJar 생성
5. Jib로 Docker Hub 이미지 푸시
6. Kubernetes 매니페스트 렌더링
7. k3s 자동 배포

배포 전제 조건:

- Jenkins credential 설정 완료
- Docker Hub credential 등록
- `smartcane` namespace 및 RBAC 적용 완료
- backend secret 생성 완료

현재 상태:

- 자동 빌드/배포 동작 중
- 외부 health check 성공
- `/api` ingress rewrite 적용 완료
