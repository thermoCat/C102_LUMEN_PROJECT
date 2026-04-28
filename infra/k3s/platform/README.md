# SmartCane k3s Platform

이 디렉터리는 SmartCane의 k3s 플랫폼 운영 문서를 모아둔 곳입니다.

현재 운영 중인 구조는 AWS private 네트워크 기반이 아니라, `Tailscale` 가상망 위에서 다시 구성한 `k3s` 멀티노드 클러스터입니다.

## 현재 클러스터 상태

- A: control-plane
  - 노드명: `ip-172-26-4-199`
  - Tailscale IP: `100.85.219.60`
  - 역할: `k3s server`, `kubectl` 실행 기준 노드, Traefik 진입점 중 하나
- B: worker
  - 노드명: `ip-172-31-45-62`
  - Tailscale IP: `100.71.123.38`
  - 역할: 애플리케이션 워크로드 실행
- C: worker
  - 노드명: `ip-172-31-36-235`
  - Tailscale IP: `100.85.80.94`
  - 역할: 애플리케이션 워크로드 실행

## 이 구조를 어떻게 봐야 하는가

- 이 클러스터는 `멀티노드 분산 처리` 구조다.
- backend 워크로드는 여러 노드에 분산 배치될 수 있다.
- `A`가 단일 control-plane 이므로 완전한 의미의 HA 클러스터는 아니다.
- 즉 서비스 레벨 분산과 확장성은 확보했지만, control-plane 고가용성까지 달성한 구조는 아니다.

## 현재 검증된 항목

- 세 노드 모두 `Ready`
- `INTERNAL-IP` 가 모두 Tailscale `100.x.x.x` 대역으로 잡힘
- backend pod가 A와 B에 실제 분산 배치되어 동작함
- `http://k14c102.p.ssafy.io/api/actuator/health` 응답 확인
- `http://k14c102.p.ssafy.io/map.html` 정적 페이지 응답 확인

## 하위 디렉터리 설명

- `monitoring/`
  - Prometheus, Grafana, Alertmanager 문서
  - 현재 새 클러스터에는 아직 재설치 전
- `dns-tls/`
  - cert-manager, external-dns 정리
  - 현재 활성 운영 구성은 아님
- `backup/`
  - k8s 리소스 백업 스크립트와 운영 메모
- `secrets/`
  - External Secrets / AWS Secrets Manager 템플릿
  - 현재는 참고용 템플릿 성격
- `gitops/`
  - Argo CD 전환용 템플릿
  - 현재는 미적용

## 운영 메모

- 지금 기준 주 운영 대상은 backend다.
- monitoring, Jenkins, DNS/TLS 는 새 Tailscale 클러스터에 다시 올려야 한다.
- 문서에 적힌 템플릿과 실제 운영 상태를 혼동하지 않도록, 각 하위 README에서 `현재 적용 여부`를 별도로 확인한다.
