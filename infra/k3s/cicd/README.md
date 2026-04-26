# SmartCane Jenkins CI/CD on k3s

이 디렉터리는 SmartCane k3s 환경에서 사용하는 Jenkins 설정과 파이프라인 자산을 모아둔 곳입니다.

## 1. 현재 구조

- Jenkins는 k3s 클러스터의 `cicd` 네임스페이스에서 동작합니다.
- Jenkins controller는 A 노드에 고정되어 있습니다.
- PVC 기반 영속 스토리지를 사용합니다.
- 외부 접속은 `8989 -> 8080` 포트포워딩 서비스로 노출되어 있습니다.
- GitLab webhook이 `develop` push를 자동 트리거합니다.

## 2. 주요 파일

- [jenkins-values.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/jenkins-values.yaml)
  - Jenkins Helm 설치 설정
- [Jenkinsfile](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/Jenkinsfile)
  - 현재 파이프라인 정의
- [render-backend-manifests.sh](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/scripts/render-backend-manifests.sh)
  - 백엔드 매니페스트를 이미지 태그 기준으로 렌더링
- [deploy-backend.sh](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/scripts/deploy-backend.sh)
  - 렌더링된 매니페스트를 k3s에 적용

## 3. 현재 파이프라인 흐름

현재 파이프라인은 `develop` 브랜치 기준으로 아래 흐름을 따릅니다.

1. 소스 checkout
2. 저장소 구조 검증
3. backend Gradle Wrapper 테스트
4. `bootJar` 생성
5. 이미지 태그 메타데이터 생성
6. 선택적으로 컨테이너 이미지 빌드
7. 선택적으로 이미지 push
8. Kubernetes 매니페스트 렌더링
9. 선택적으로 k3s 배포

## 4. 브랜치 정책

현재 파이프라인은 `develop` 브랜치만 허용합니다.

브랜치는 두 군데에서 확인합니다.

- Jenkins job branch specifier: `*/develop`
- Jenkinsfile branch policy stage

## 5. 런타임 플래그

파이프라인은 아래 환경 플래그를 사용합니다.

- `ENABLE_IMAGE_BUILD`
  - `true`면 `docker build` 수행
- `ENABLE_IMAGE_PUSH`
  - `true`면 `docker push` 수행
- `ENABLE_K8S_DEPLOY`
  - `true`면 `kubectl` 배포 수행

기본값은 모두 `false`라서, 레지스트리/배포 권한이 준비되기 전까지는 안전한 CI 우선 모드로 돌릴 수 있습니다.

## 6. Registry / Secret 전략

- GitLab 접근은 Jenkins credential로 처리합니다.
- AWS key, DB 비밀번호, JWT secret, 실제 Kubernetes secret 파일은 Git에 올리지 않습니다.
- 실제 secret 값은 Jenkins credential 또는 클러스터 쪽 별도 secret 생성으로 관리합니다.
- 저장소에는 `secret.example.yaml`만 유지합니다.

## 7. 다음 단계
- 컨테이너 레지스트리 credential
- 실제 이미지 repository 값
- Jenkins 런타임의 Docker 접근 권한
- Jenkins 런타임의 `kubectl` 접근 권한
- 대상 클러스터용 실제 backend secret 값
