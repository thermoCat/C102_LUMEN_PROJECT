# Jenkins + GitLab + Tailscale k3s Architecture

이 문서는 SmartCane의 현재 CI/CD 목표 구조를 설명합니다.

현재 backend는 이미 새 Tailscale 기반 k3s 클러스터에서 정상 동작하고 있고, Jenkins는 아직 같은 클러스터 위에 다시 올리기 전 단계입니다.

## 현재 인프라 구조

### A 노드

- 노드명: `ip-172-26-4-199`
- 역할: `k3s server`
- Tailscale IP: `100.85.219.60`
- 설명:
  - control-plane
  - `kubectl` 실행 기준 노드
  - Traefik 진입점 중 하나
  - backend pod가 올라갈 수 있는 app 노드

### B 노드

- 노드명: `ip-172-31-45-62`
- 역할: `k3s agent`
- Tailscale IP: `100.71.123.38`
- 설명:
  - backend 등 애플리케이션 워크로드 실행
  - 실제 backend pod 기동 확인 완료

### C 노드

- 노드명: `ip-172-31-36-235`
- 역할: `k3s agent`
- Tailscale IP: `100.85.80.94`
- 설명:
  - 추가 워크로드 실행 노드

## 현재 구조를 한 줄로 정리하면

- `단일 control-plane + 다중 worker`
- `Tailscale 기반 멀티노드 분산 처리`
- `완전한 HA 클러스터는 아님`

## 왜 이 구조를 선택했는가

이전에는 AWS private 네트워크 제약 때문에:

- A와 B/C가 직접 private 통신하지 못했고
- node 는 `Ready` 여도 worker pod endpoint 접근이 실패했으며
- cert-manager, monitoring, worker ingress 경로가 불안정했다

이를 해결하기 위해 A/B/C 위에 Tailscale 가상망을 먼저 붙이고, 그 위에 k3s를 새로 재구성했다.

그 결과:

- 노드 간 제어 경로와 데이터 경로를 Tailscale 인터페이스로 통일
- `INTERNAL-IP` 가 모두 `100.x.x.x`
- backend가 실제로 여러 노드에 분산 실행

## 현재 서비스 흐름

```text
Developer
  -> git push develop
  -> GitLab webhook
  -> Jenkins pipeline
  -> backend test / build / image push
  -> rendered manifests
  -> kubectl apply
  -> k3s cluster
  -> Traefik ingress
  -> external traffic
```

## 현재 검증 완료 상태

- 새 k3s 클러스터 재구성 완료
- backend 수동 배포 완료
- backend pod가 A와 B에 분산 배치되어 `Running`
- `/api/actuator/health` 외부 응답 확인

## 아직 남아 있는 작업

- Jenkins를 새 클러스터에 재설치
- monitoring 재설치
- 필요 시 DNS/TLS 재정비

## 주의할 점

- Jenkinsfile의 `K8S_API_SERVER` 는 새 구조에 맞게 Tailscale 주소로 갱신해야 한다
- backend 매니페스트의 기본 이미지는 placeholder 이므로 CI 렌더링이 필요하다
- 이 구조는 멀티노드 분산 처리에는 적합하지만, control-plane HA를 의미하지는 않는다
