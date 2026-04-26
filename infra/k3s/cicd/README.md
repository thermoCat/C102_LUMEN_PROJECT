# SmartCane Jenkins CI/CD on k3s

이 디렉터리는 SmartCane k3s 환경에서 사용하는 Jenkins 설정, 파이프라인, 배포 보조 스크립트를 모아둔 곳입니다.

## 1. 현재 구조

- Jenkins는 k3s 클러스터의 `cicd` 네임스페이스에서 동작합니다.
- Jenkins controller는 A 노드에 고정되어 있습니다.
- PVC 기반 영속 스토리지를 사용합니다.
- 외부 접속은 `8989 -> 8080` 포트포워딩 서비스로 노출되어 있습니다.
- GitLab webhook이 `develop` 브랜치 push를 자동 트리거합니다.

## 2. 주요 파일

- [jenkins-values.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/jenkins-values.yaml)
  - Jenkins Helm 설치 설정
- [Jenkinsfile](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/Jenkinsfile)
  - 현재 CI/CD 파이프라인 정의
- [render-backend-manifests.sh](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/scripts/render-backend-manifests.sh)
  - backend 매니페스트를 이미지 태그 기준으로 렌더링
- [deploy-backend.sh](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/scripts/deploy-backend.sh)
  - 렌더링된 매니페스트를 k3s에 적용
- [jenkins-smartcane-deployer.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/rbac/jenkins-smartcane-deployer.yaml)
  - Jenkins ServiceAccount에 `smartcane` 네임스페이스 배포 권한 부여

## 3. 현재 파이프라인 흐름

현재 파이프라인은 `develop` 브랜치 기준으로 아래 순서로 동작합니다.

1. 소스 checkout
2. 브랜치 정책 검증
3. 저장소 구조 검증
4. backend Gradle Wrapper 테스트
5. `bootJar` 생성
6. 이미지 태그 메타데이터 생성
7. 선택적으로 컨테이너 이미지 빌드
8. 선택적으로 이미지 push
9. Kubernetes 매니페스트 렌더링
10. 선택적으로 k3s 배포

## 4. 브랜치 정책

현재 파이프라인은 `develop` 브랜치만 허용합니다.

브랜치 확인은 두 곳에서 이뤄집니다.

- Jenkins job branch specifier: `*/develop`
- Jenkinsfile 내부 branch policy stage

## 5. 파이프라인 환경변수

현재 파이프라인에서 사용하는 주요 환경변수는 아래와 같습니다.

- `PRIMARY_BRANCH`
  - 기본값: `develop`
- `BACKEND_APP_DIR`
  - 기본값: `backend`
- `K8S_NAMESPACE`
  - 기본값: `smartcane`
- `BACKEND_INGRESS_HOST`
  - 기본값: `api.smartcane.com`
- `BACKEND_IMAGE_REPOSITORY`
  - 기본값: `change-me/backend-api`
- `ENABLE_IMAGE_BUILD`
  - `true`면 `docker build` 수행
- `ENABLE_IMAGE_PUSH`
  - `true`면 `docker push` 수행
- `ENABLE_K8S_DEPLOY`
  - `true`면 `kubectl` 배포 수행

기본값은 보수적으로 잡혀 있어서, 지금은 CI 우선 모드로 안전하게 돌릴 수 있습니다.

## 6. Jenkins Credential 권장 이름 규칙

현재 저장소에서는 실제 credential 값을 커밋하지 않고, Jenkins 쪽에서 아래처럼 이름을 맞춰 관리하는 방식을 권장합니다.

### 필수

- `gitlab-read-repo`
  - GitLab 저장소 checkout용 PAT

### 이미지 빌드 / push 단계에서 권장

- `docker-registry-smartcane`
  - Docker Hub 또는 사설 레지스트리 계정

### AWS 연동이 필요한 경우 권장

- `aws-smartcane-runtime`
  - AWS access key / secret key

### k3s 배포 자동화 시 권장

- `kubeconfig-smartcane`
  - 클러스터 배포용 kubeconfig 또는 kubectl 접근 정보

즉, 실제 값은 Git 대신 Jenkins credential에 두고, 파이프라인은 그 이름을 기준으로 참조하도록 확장하는 방향이 맞습니다.

## 7. Secret / ConfigMap 전략

- 비밀이 아닌 값
  - ConfigMap
  - 예: host, port, bucket, prefix, region
- 비밀 값
  - Kubernetes Secret 또는 Jenkins credential
  - 예: DB 비밀번호, Redis 비밀번호, AWS key, JWT secret

저장소에는 실제 `secret.yaml`을 두지 않고 아래 파일만 유지합니다.

- [secret.example.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/apps/backend/secret.example.yaml)

## 8. 배포 권한 방식

현재는 외부 `kubeconfig`를 Jenkins에 업로드하는 대신, k3s 안에서 동작 중인 Jenkins의 ServiceAccount에 namespace 단위 RBAC를 부여하는 방식을 권장합니다.

적용 파일:

- [jenkins-smartcane-deployer.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/rbac/jenkins-smartcane-deployer.yaml)

적용 예시:

```bash
kubectl apply -f infra/k3s/cicd/rbac/jenkins-smartcane-deployer.yaml
```

이 방식의 장점:

- 외부 kubeconfig 파일 관리가 필요 없음
- Jenkins 권한 범위를 `smartcane` 네임스페이스로 제한 가능
- 운영 구조가 더 단순함

## 9. 현재 단계에서 이미 되는 것

- GitLab webhook 기반 자동 실행
- `develop` 브랜치 기준 파이프라인 실행
- `backend/gradlew test`
- `backend/gradlew bootJar`
- 이미지 태그 계산
- 매니페스트 렌더링

## 10. 아직 남은 것

실제 CD까지 닫으려면 아래가 더 필요합니다.

- 컨테이너 레지스트리 credential
- 실제 이미지 repository 값
- Jenkins 런타임의 Docker 접근 권한
- Jenkins ServiceAccount RBAC 적용
- 대상 클러스터에 실제 backend secret 생성

## 11. 다음 추천 순서

1. Jenkins credential 이름 확정
2. `jenkins-smartcane-deployer.yaml` 적용
3. 실제 레지스트리 결정
4. `ENABLE_IMAGE_BUILD=true` 검증
5. `ENABLE_IMAGE_PUSH=true` 검증
6. `ENABLE_K8S_DEPLOY=true` 검증
7. 최종 E2E 확인
