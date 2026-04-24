# Jenkins CI/CD 정리

이 문서는 SmartCane k3s 클러스터 위에서 Jenkins를 운영하는 현재 기준을 정리한 문서입니다.

## 1. 현재 방향

Jenkins는 별도 Docker 단일 인스턴스가 아니라, 현재 k3s 클러스터 안에서 운영합니다.

현재 실제 방향:

```text
A 노드
- Jenkins controller
- 영속 볼륨(PVC) 사용

B / C 노드
- 필요 시 이후 agent / workload 확장 대상
```

Jenkins는 현재 A 노드에 고정해 두었습니다.

이유:

- 클러스터가 완전한 HA control-plane 구조는 아님
- Jenkins는 우선 안정적으로 떠야 함
- 영속 PVC 바인딩이 A에서 가장 안정적으로 확인됨

## 2. 설치 파일 역할

현재 `infra/k3s/cicd` 아래 파일 역할은 다음과 같습니다.

- [jenkins-values.yaml](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\cicd\jenkins-values.yaml)
  - Jenkins Helm 설치용 설정 파일
- [Jenkinsfile](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\cicd\Jenkinsfile)
  - GitLab에서 Jenkins가 읽어 실행할 실제 파이프라인 파일
- [README.md](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\cicd\README.md)
  - 현재 운영 기준 문서

## 3. 설치 명령

namespace 생성:

```bash
sudo kubectl create namespace cicd
```

Helm repo 등록:

```bash
helm repo add jenkins https://charts.jenkins.io
helm repo update
```

설치 또는 업그레이드:

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
sudo -E helm upgrade --install jenkins jenkins/jenkins \
  -n cicd \
  -f /home/ubuntu/jenkins-values.yaml
```

## 4. 현재 설치 기준 요약

현재 values 기준 핵심은 아래와 같습니다.

- A 노드 고정
- PVC 사용
- local-path 스토리지 사용
- JCasC 기본 자동 구성은 끔
- 플러그인 선설치는 최소화

즉 Jenkins를 먼저 안정적으로 띄우고, 이후 필요한 플러그인과 파이프라인을 단계적으로 얹는 방향입니다.

## 5. 현재 상태 확인

```bash
sudo kubectl get pods -n cicd -o wide
sudo kubectl get pvc -n cicd
sudo kubectl get svc -n cicd
```

정상 기준:

```text
jenkins-0   1/1 Running
jenkins PVC Bound
```

## 6. 접속 방식

현재는 SSH 터널 + port-forward 방식으로 접속합니다.

로컬 PC:

```cmd
ssh -i "C:\Users\SSAFY\Downloads\K14C102T.pem" -L 8080:localhost:8080 ubuntu@43.202.33.39
```

A 서버:

```bash
sudo kubectl -n cicd port-forward svc/jenkins 8080:8080
```

브라우저:

```text
http://localhost:8080
```

## 7. GitLab 연동 기준

Jenkins는 GitLab 저장소를 읽어서 파이프라인을 실행합니다.

권장 방식:

- Repository URL:
  - `https://lab.ssafy.com/s14-final/S14P31C102.git`
- Credentials:
  - `Username with password`
  - username = GitLab 아이디
  - password = GitLab Personal Access Token
- 최소 권한:
  - `read_repository`

## 8. Jenkins job 기준

현재는 `Pipeline` job을 사용합니다.

권장 설정:

- Definition:
  - `Pipeline script from SCM`
- SCM:
  - `Git`
- Branch:
  - `*/develop`
- Script Path:
  - `infra/k3s/cicd/Jenkinsfile`

## 9. 현재 Jenkinsfile 성격

[infra/k3s/cicd/Jenkinsfile](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\k3s\cicd\Jenkinsfile)은 지금 저장소 상태에 맞춘 **bootstrap CI** 파이프라인입니다.

현재 저장소에는 앱 빌드 소스가 거의 없으므로, 우선 다음만 검증합니다.

- GitLab checkout
- 브랜치/워크스페이스 확인
- 필수 인프라 파일 존재 확인

앱 소스가 올라오면 그 다음에 아래 단계를 추가합니다.

- test
- build
- image push
- k3s deploy

## 10. 현재 정리

지금 Jenkins 관련 결론은 다음과 같습니다.

- Jenkins는 k3s 위에서 운영
- Jenkins controller는 A 노드에서 동작
- 영속 PVC 사용
- GitLab 기반 Pipeline job 사용
- 실행용 Jenkinsfile은 `infra/k3s/cicd/Jenkinsfile` 하나로 통일

## 11. 다음 작업

1. `infra/k3s/cicd/Jenkinsfile`을 `develop` 브랜치에 커밋
2. GitLab에 push
3. Jenkins job에서 `Build Now`로 bootstrap pipeline 성공 확인
4. 이후 실제 앱 코드 구조에 맞춰 Jenkinsfile 확장
