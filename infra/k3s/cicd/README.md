# SmartCane CI/CD

이 디렉터리는 SmartCane backend를 Jenkins로 빌드하고 k3s에 배포하기 위한 CI/CD 구성 파일을 담고 있습니다.

## 현재 상태

- backend용 CI/CD 파일은 정리되어 있음
- 새 Tailscale 기반 k3s 클러스터에서는 Jenkins를 아직 재설치하지 않음
- 따라서 이 디렉터리는 `현재 동작 중인 Jenkins 운영 상태` 라기보다 `재설치 시 사용할 기준 구성` 으로 봐야 한다

## 포함 파일

- `Jenkinsfile`
- `jenkins-values.yaml`
- `rbac/jenkins-smartcane-deployer.yaml`
- `scripts/render-backend-manifests.sh`
- `scripts/deploy-backend.sh`
- `ARCHITECTURE.md`

## 파이프라인 개요

1. GitLab `develop` 브랜치 변경 감지
2. backend 테스트 실행
3. backend `bootJar` 생성
4. Jib로 Docker Hub 이미지 push
5. backend 매니페스트 렌더링
6. `kubectl apply` 로 k3s 배포

## 현재 이미지 전략

- Docker Hub repository: `kjw3568/smartcane-backend`
- 이미지 태그 형식:

```text
<branch>-<buildNumber>-<shortCommit>
```

실제 예시:

```text
develop-32-ca37e16
```

## 렌더링 스크립트 동작

[`render-backend-manifests.sh`](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\cicd\scripts\render-backend-manifests.sh) 는 아래 값을 치환한다.

- `K8S_NAMESPACE`
- `BACKEND_IMAGE`
- `BACKEND_INGRESS_HOST`

즉 `infra/k3s/apps/backend/deployment.yaml` 에 placeholder 이미지가 있어도, CI 경로에서는 실제 이미지 태그로 치환 가능하다.

## 새 클러스터 기준으로 꼭 바꿔야 하는 값

현재 [`Jenkinsfile`](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\cicd\Jenkinsfile) 에는 아래 값이 남아 있다.

```text
K8S_API_SERVER = https://172.26.4.199:6443
```

하지만 새 클러스터는 Tailscale 기반이므로, Jenkins를 다시 올릴 때는 아래 값으로 바꿔야 한다.

```text
K8S_API_SERVER = https://100.85.219.60:6443
```

## Jenkins 재설치 전 체크리스트

- Jenkins namespace 구성
- Jenkins Helm 배포
- Jenkins serviceAccount + RBAC 적용
- Docker Hub credential 재등록
- GitLab webhook 재연결
- `K8S_API_SERVER` 를 Tailscale 주소로 수정
- backend secret / namespace 존재 확인

## 배포에 필요한 전제

- `smartcane` namespace 존재
- backend secret 존재
- A control-plane 에서 k3s API 접근 가능
- Jenkins Pod가 Tailscale 기반 클러스터 안에서 동작

## 현재 결론

- backend는 이미 수동으로 새 멀티노드 클러스터에 복구 완료
- Jenkins는 아직 새 클러스터에 재설치 전
- 따라서 이 문서는 `다음 Jenkins 복구 작업의 기준 문서` 로 사용한다
