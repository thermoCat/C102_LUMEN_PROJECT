# SmartCane Backend 배포 템플릿

이 디렉터리는 최소 SmartCane backend 애플리케이션을 k3s 클러스터에 배포하기 위한 Kubernetes 매니페스트를 모아둔 곳입니다.

## 1. 포함 파일

- [namespace.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/apps/backend/namespace.yaml)
- [configmap.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/apps/backend/configmap.yaml)
- [secret.example.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/apps/backend/secret.example.yaml)
- [deployment.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/apps/backend/deployment.yaml)
- [service.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/apps/backend/service.yaml)
- [ingress.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/apps/backend/ingress.yaml)

## 2. 각 파일 역할

- `namespace.yaml`
  - 대상 네임스페이스 생성
- `configmap.yaml`
  - `backend-config` 비밀이 아닌 런타임 설정 저장
- `secret.example.yaml`
  - `backend-secret`에 들어갈 키 예시 제공
- `deployment.yaml`
  - Spring Boot backend 실행
  - health probe 설정 포함
- `service.yaml`
  - `backend-service`를 클러스터 내부에 노출
- `ingress.yaml`
  - Traefik 기준 외부 HTTP 라우팅 정의

## 3. 수동 적용 순서

실제 `secret.yaml`을 `secret.example.yaml` 기준으로 만든 뒤 아래 순서로 적용하면 됩니다.

```bash
kubectl apply -f infra/k3s/apps/backend/namespace.yaml
kubectl apply -f infra/k3s/apps/backend/configmap.yaml
kubectl apply -f infra/k3s/apps/backend/secret.yaml
kubectl apply -f infra/k3s/apps/backend/deployment.yaml
kubectl apply -f infra/k3s/apps/backend/service.yaml
kubectl apply -f infra/k3s/apps/backend/ingress.yaml
```

## 4. Secret 작성 기준

실제 `secret.yaml`에는 아래 종류의 값이 들어갑니다.

- PostgreSQL RDS 계정
- MySQL RDS 계정
- Redis ElastiCache 비밀번호
- AWS access key / secret key
- JWT secret

즉 host, port, bucket 같은 값은 ConfigMap으로 두고, 비밀번호/키 계열은 Secret으로 분리하는 방식입니다.

## 5. Jenkins 배포 권한 전제

Jenkins가 이 매니페스트를 실제 배포하려면 `smartcane` 네임스페이스에 대한 권한이 있어야 합니다.

현재 권장 방식은 외부 kubeconfig 업로드가 아니라, Jenkins ServiceAccount에 namespace 단위 Role/RoleBinding을 부여하는 것입니다.

관련 파일:

- [jenkins-smartcane-deployer.yaml](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/k3s/cicd/rbac/jenkins-smartcane-deployer.yaml)

## 6. Jenkins CD 흐름

이 디렉터리의 매니페스트는 Jenkins 파이프라인이 아래 흐름으로 확장되는 것을 기준으로 준비되어 있습니다.

1. 소스 checkout
2. backend test
3. `bootJar` 생성
4. 컨테이너 이미지 빌드
5. 레지스트리 push
6. 최종 이미지 태그 기준 매니페스트 렌더링
7. k3s 적용

## 7. 기본 가정

- ingress controller: Traefik
- 대상 namespace: `smartcane`
- backend service 이름: `backend-service`
- 애플리케이션 포트: `8080`
- liveness/readiness endpoint:
  - `/actuator/health/liveness`
  - `/actuator/health/readiness`
