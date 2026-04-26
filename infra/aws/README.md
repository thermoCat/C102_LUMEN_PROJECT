# SmartCane AWS 인프라 정리

이 문서는 현재 실제로 구축한 SmartCane AWS / k3s 인프라 상태를 기준으로 정리한 문서입니다.

현재 최종 구조는 `단일 control-plane + 2 agent` 기반의 멀티노드 k3s 클러스터입니다.

## 1. 최종 인프라 구조

```text
A 노드  t3a.xlarge
- k3s server (control-plane, etcd)
- Jenkins
- 주요 애플리케이션 진입 노드

B 노드  t3a.xlarge
- k3s agent
- ops 성격 워크로드 우선 배치

C 노드  t3a.large
- k3s agent
- app-lite 성격 워크로드 우선 배치
```

## 2. 실제 노드 정보

| 노드 | 역할 | Public IP | Private IP | 상태 |
| --- | --- | --- | --- | --- |
| A | k3s server | `43.202.33.39` | `172.26.4.199` | 운영 중 |
| B | k3s agent | `54.116.86.121` | `172.31.45.62` | 운영 중 |
| C | k3s agent | `43.202.250.196` | `172.31.36.235` | 운영 중 |

## 3. 네트워크 구조

A 노드는 `172.26.0.0/16`, B/C 노드는 `172.31.0.0/16` 대역에 있습니다.

실제 확인 결과 private IP 기준 직접 통신이 되지 않아, embedded etcd 기반 HA control-plane 구성은 불가능했습니다.

그래서 최종 구조를 아래처럼 확정했습니다.

- `A = server`
- `B/C = agent`

즉 현재 클러스터는 멀티노드이지만 control-plane HA 구조는 아닙니다.

## 4. 데이터 계층

애플리케이션 데이터 계층은 외부 관리형 서비스를 사용합니다.

- PostgreSQL + PostGIS
- MySQL
- Redis
- S3

즉 서버 쪽은 애플리케이션 실행, 운영 도구, CI/CD에 집중하고, 데이터 저장소는 외부 서비스에 맡기는 구조입니다.

## 5. Swap

각 노드에는 16GB swap을 설정했습니다.

확인 명령:

```bash
free -h
swapon --show
```

## 6. k3s 구성 상태

확인 명령:

```bash
sudo kubectl get nodes -o wide
```

기대 상태:

```text
A: Ready / control-plane,etcd
B: Ready / <none>
C: Ready / <none>
```

## 7. 노드 라벨

현재 클러스터에는 아래 라벨을 사용합니다.

```text
A: node.smartcane/role=app,      node.smartcane/size=xlarge
B: node.smartcane/role=ops,      node.smartcane/size=xlarge
C: node.smartcane/role=app-lite, node.smartcane/size=large
```

주의:
- 프로젝트 내부 이름을 조정하더라도
- 현재 실제 클러스터 라벨 키는 이미 적용된 `node.smartcane/*`를 그대로 유지합니다.
- 이 값을 바꾸려면 실제 노드 relabel 작업이 추가로 필요합니다.

확인 명령:

```bash
sudo kubectl get nodes --show-labels
```

## 8. 주요 포트

A 노드는 `ufw`를 사용 중이며, B/C public IP 기준으로 필요한 포트만 허용했습니다.

주요 포트:

```text
6443/tcp   Kubernetes API
10250/tcp  kubelet
8472/udp   flannel VXLAN
```

`2379`, `2380`은 HA control-plane 용도이므로 현재 최종 구조에서는 필수 포트가 아닙니다.

## 9. Bastion / tunnel 스크립트

`infra/aws` 아래 파일:

- [tunnel-postgres.ps1](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/aws/tunnel-postgres.ps1)
- [tunnel-mysql.ps1](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/aws/tunnel-mysql.ps1)
- [tunnel-redis.ps1](/C:/Users/SSAFY/IdeaProjects/S14P31C102/infra/aws/tunnel-redis.ps1)

이 파일들은 로컬 PC에서 bastion을 통해 DB/Redis에 접속할 때 쓰는 보조 스크립트입니다.

즉:

- 애플리케이션 운영에 필수는 아님
- 로컬 점검과 디버깅 용도
- 운영 문서에서는 “선택 도구”로 보면 됩니다

## 10. 현재 인프라 요약

현재 인프라는 아래 기준으로 보면 됩니다.

- SmartCane 프로젝트용 AWS + k3s 기반 구조
- 단일 control-plane + 2 agent 멀티노드 클러스터
- 외부 관리형 DB/Cache 사용
- Jenkins는 k3s 위에서 운영

## 11. 다음 인프라 작업

다음 작업은 보통 아래 순서로 이어집니다.

1. 실제 애플리케이션 배포
2. Ingress / 서비스 외부 노출 구성
3. Jenkins CD 단계 확장
4. 모니터링 정리
5. E2E 검증
