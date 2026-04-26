# Jenkins + k3s + GitLab 전체 구조 정리

이 문서는 현재 SmartCane 프로젝트에서 실제로 구성한 `GitLab + Jenkins + k3s` 흐름을 한 번에 이해할 수 있도록 정리한 문서입니다.

현재 기준은 다음과 같습니다.

- Git 저장소: GitLab
- CI 서버: Jenkins
- 실행 환경: k3s 멀티노드 클러스터
- 배포 구조: `단일 control-plane + 2 agent`
- Jenkins 외부 접속: `k14c102.p.ssafy.io:8989`
- Jenkins 자동 트리거: GitLab Webhook

---

## 1. 한눈에 보는 전체 흐름

```text
개발자
  |
  | git push (develop)
  v
GitLab 저장소
  |
  | webhook
  v
Jenkins
  |
  | Jenkinsfile 실행
  v
CI 단계
  - 저장소 checkout
  - 파일 / 브랜치 / 기본 구조 확인
  - 이후 테스트 / 빌드 / 이미지 생성 / 배포로 확장 가능
  |
  v
k3s 클러스터
  - A: control-plane + Jenkins
  - B: agent
  - C: agent
```

---

## 2. 현재 실제 인프라 구성

### A 노드

- 인스턴스: `t3a.xlarge`
- 역할:
  - `k3s server`
  - `control-plane`
  - `etcd`
  - `Jenkins controller`
- Public IP: `43.202.33.39`
- Private IP: `172.26.4.199`

### B 노드

- 인스턴스: `t3a.xlarge`
- 역할:
  - `k3s agent`
  - ops 성격 워크로드 후보
- Public IP: `54.116.86.121`
- Private IP: `172.31.45.62`

### C 노드

- 인스턴스: `t3a.large`
- 역할:
  - `k3s agent`
  - app-lite 성격 워크로드 후보
- Public IP: `43.202.250.196`
- Private IP: `172.31.36.235`

---

## 3. 왜 HA control-plane이 아닌가

처음에는 A, B, C를 모두 k3s server로 올려서 HA control-plane 구성을 검토했습니다.

하지만 실제 확인 결과:

- A는 `172.26.0.0/16` 대역
- B/C는 `172.31.0.0/16` 대역
- private IP 기준 직접 통신 불가

즉 embedded etcd 기반 HA control-plane을 위한 private peer 통신이 성립하지 않았습니다.

그래서 최종 구조는 아래처럼 정리했습니다.

```text
A = k3s server
B = k3s agent
C = k3s agent
```

이 구조는:

- 멀티노드 클러스터는 맞음
- 워크로드 분산 가능
- 완전한 control-plane HA는 아님

---

## 4. Jenkins를 왜 k3s 위에 올렸는가

예전에는 단일 인스턴스에서 Docker Compose로 Jenkins를 띄우는 방식도 쓸 수 있었습니다.

예:

```text
EC2 1대
 -> Docker
 -> docker-compose
 -> Jenkins
```

하지만 지금은 인프라 중심이 k3s 멀티노드 클러스터로 이동했기 때문에 Jenkins도 클러스터 안에서 운영하는 쪽으로 방향을 맞췄습니다.

즉 현재는:

```text
k3s cluster
 -> Jenkins Pod
 -> Jenkins PVC
 -> Jenkins Service
```

구조로 동작합니다.

### 장점

- 운영 도구도 클러스터 안에서 관리 가능
- Jenkins를 k8s 리소스로 볼 수 있음
- 나중에 앱 배포와 운영 흐름이 자연스럽게 연결됨
- PVC, nodeSelector, service exposure 등을 활용 가능

### 단점

- Docker Compose보다 복잡함
- Helm, PVC, Service, Port-forward, Webhook 등을 같이 이해해야 함
- 초기 디버깅 난이도가 높음

---

## 5. Jenkins 관련 파일 역할

현재 `infra/k3s/cicd` 아래 파일 역할은 다음과 같습니다.

### [jenkins-values.yaml](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\cicd\jenkins-values.yaml)

Jenkins Helm 설치용 설정 파일입니다.

이 파일에서 관리하는 것:

- Jenkins가 어느 노드에 뜰지
- PVC를 쓸지
- StorageClass를 무엇으로 쓸지
- sidecar / plugin 초기화 관련 옵션
- 리소스 요청/제한

### [Jenkinsfile](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\cicd\Jenkinsfile)

GitLab에서 Jenkins가 읽어서 실제로 실행하는 파이프라인 파일입니다.

즉:

- Jenkins job의 `Script Path`
- `infra/k3s/cicd/Jenkinsfile`

로 연결되는 파일입니다.

### [README.md](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\cicd\README.md)

Jenkins 설치/운영 기준을 정리한 문서입니다.

### [ARCHITECTURE.md](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\cicd\ARCHITECTURE.md)

현재 문서입니다.

GitLab, Jenkins, k3s 전체 관계와 구조를 설명합니다.

---

## 6. Jenkins는 현재 어떻게 노출되어 있는가

Jenkins는 내부적으로 `8080` 포트를 사용합니다.

하지만 외부에서 접근하기 위해 현재는:

```text
k14c102.p.ssafy.io:8989 -> Jenkins 8080
```

형태로 연결해 두었습니다.

이 연결은 A 서버에서 `kubectl port-forward`를 systemd 서비스로 상시 실행하는 방식입니다.

즉 개념적으로는:

```text
외부 요청
 -> A 서버 8989
 -> systemd가 유지하는 kubectl port-forward
 -> svc/jenkins:8080
 -> Jenkins
```

입니다.

### 왜 이렇게 했는가

당장 가장 빠르게:

- 외부 접속 가능
- GitLab webhook 가능
- 추가 콘솔 작업 최소화

를 만족시키기 위해서입니다.

### 이 방식의 의미

- `SSH 터널`은 더 이상 필요 없음
- 대신 A 서버의 `jenkins-port-forward.service`는 살아 있어야 함
- 정식 Ingress/NodePort보다는 임시 운영형이지만, 현재 프로젝트 단계에서는 충분히 실용적임

---

## 7. NodePort는 무엇인가

NodePort는 Kubernetes Service를 외부에서 접근 가능하게 여는 방법 중 하나입니다.

예:

```text
A서버IP:32080 -> Jenkins:8080
```

특징:

- `kubectl port-forward` 없이 외부 공개 가능
- webhook에 적합
- 다만 보통 포트가 `30000~32767`
- 현재처럼 `8989`를 유지하기는 불편할 수 있음

즉 NodePort는 더 쿠버네티스다운 노출 방식이고,  
현재 방식은 그보다 빠르게 붙인 현실적인 노출 방식이라고 보면 됩니다.

---

## 8. bootstrap pipeline이란 무엇인가

현재 Jenkinsfile은 아직 실제 앱 빌드/배포용 파이프라인이 아닙니다.

현재 성격은 `bootstrap pipeline`입니다.

이 말은:

- Jenkins와 GitLab이 잘 연결되었는지
- `develop` 브랜치를 읽는지
- Jenkinsfile을 정상 인식하는지
- 저장소 구조가 기본 기준을 만족하는지

를 먼저 검증하는 **기초 연결 테스트 파이프라인**이라는 뜻입니다.

현재 Jenkinsfile이 하는 일:

- checkout
- branch / workspace 확인
- 필수 파일 존재 확인
- 이후 확장 안내 출력

현재 Jenkinsfile이 아직 안 하는 일:

- 테스트
- 빌드
- Docker 이미지 생성
- 이미지 push
- k3s 배포

즉 현재는:

```text
자동 CI 연결 성공 여부 확인 단계
```

라고 보면 됩니다.

---

## 9. GitLab과 Jenkins는 어떻게 연결되었는가

현재 연결 방식은 다음과 같습니다.

### 1. GitLab 저장소

- 저장소: `https://lab.ssafy.com/s14-final/S14P31C102.git`
- 기본 작업 브랜치: `develop`

### 2. Jenkins Credential

Jenkins에는 GitLab 접속용 credential을 등록했습니다.

형태:

- `Username with password`
- username = GitLab 아이디
- password = GitLab Personal Access Token

권한:

- `read_repository`

### 3. Jenkins Pipeline Job

현재 Jenkins Job은 `Pipeline` 타입입니다.

주요 설정:

- SCM: `Git`
- Branch: `*/develop`
- Script Path: `infra/k3s/cicd/Jenkinsfile`

### 4. GitLab Webhook

GitLab에서 push 이벤트가 발생하면 Jenkins가 자동으로 반응하도록 webhook을 연결했습니다.

즉:

```text
git push origin develop
 -> GitLab
 -> webhook
 -> Jenkins
 -> Pipeline 실행
```

구조입니다.

---

## 10. 지금 무엇이 자동화되었는가

### 이미 자동화된 것

- GitLab push 시 Jenkins pipeline 자동 실행

즉 `자동 CI 트리거`는 완료됐습니다.

### 아직 자동화되지 않은 것

- backend 테스트
- 실제 build
- Docker image 생성
- Registry push
- k3s rollout / deploy

즉 현재 상태를 정확히 말하면:

```text
자동 CI 트리거 완료
실제 CD는 아직 미구현
```

입니다.

---

## 11. CI와 CD의 차이

### CI

Continuous Integration

의미:

- 코드를 push하면 자동으로 검증
- 테스트
- 기본 빌드
- 파이프라인 연결 확인

현재 우리가 완료한 범위:

- GitLab push
- Jenkins 자동 실행
- bootstrap pipeline 실행

### CD

Continuous Delivery / Deployment

의미:

- 빌드 결과물을 실제 실행 환경에 배포
- Docker image push
- kubectl apply / rollout

현재는 아직 여기까지는 안 갔습니다.

---

## 12. 현재 Jenkins 동작 흐름

현재 `develop` 브랜치에 push가 들어오면 다음 순서로 동작합니다.

```text
1. 개발자가 develop 브랜치에 push
2. GitLab webhook 발생
3. Jenkins가 webhook 수신
4. Jenkins job 실행
5. GitLab 저장소 checkout
6. infra/k3s/cicd/Jenkinsfile 실행
7. bootstrap 검증 수행
8. 성공/실패 로그 기록
```

---

## 13. 지금 확인할 수 있는 성공 기준

다음이 모두 되면 현재 구조는 정상입니다.

### Jenkins 접속

```text
http://k14c102.p.ssafy.io:8989
```

### k3s 상태

```bash
sudo kubectl get nodes -o wide
sudo kubectl get pods -n cicd
```

### Jenkins Pod

```text
jenkins-0   1/1 Running
```

### Jenkins PVC

```text
jenkins   Bound
```

### GitLab Webhook

GitLab webhook 테스트 응답:

```text
HTTP 200
```

### Jenkins Build

push 후 새 Build가 자동으로 생성됨

---

## 14. 지금 남은 다음 단계

이제부터는 bootstrap pipeline을 실제 앱 파이프라인으로 확장해야 합니다.

추천 순서:

1. 첫 CI/CD 대상 서비스 결정
   - 추천: `backend`
2. backend 코드 위치 확인
3. backend 빌드 명령 확정
4. Dockerfile 정리
5. 이미지 저장소 결정
6. k3s 배포 manifest 또는 Helm chart 정리
7. Jenkinsfile에 아래 단계 추가
   - test
   - build
   - image push
   - deploy

---

## 15. 현재 구조의 장점

- GitLab, Jenkins, k3s가 한 흐름으로 연결됨
- 운영 도구도 k3s 안에서 관리 가능
- webhook 기반 자동 CI 시작점 확보
- 나중에 실제 앱 배포로 확장하기 쉬움

---

## 16. 현재 구조의 한계

- control-plane HA는 아님
- Jenkins 외부 공개 방식이 정식 Ingress/NodePort는 아님
- 현재 Jenkinsfile은 bootstrap 수준
- 실제 CD는 아직 추가 구현 필요

---

## 17. 한 줄 정리

현재 SmartCane 프로젝트의 Jenkins + k3s + GitLab 구조는  
**GitLab push → Jenkins 자동 실행 → k3s 기반 운영 환경에서 파이프라인 수행**까지 연결된 상태이며,  
지금은 bootstrap CI가 성공한 단계이고 다음 작업은 이를 실제 backend 빌드·배포 파이프라인으로 확장하는 것입니다.
