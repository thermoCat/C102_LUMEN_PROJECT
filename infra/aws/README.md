# SmartCane AWS 인프라 정리

이 문서는 현재 실제로 구축한 SmartCane AWS / k3s 인프라 상태를 기준으로 정리한 문서입니다.

현재 최종 구조는 `단일 control-plane + 2 agent(worker)` 기반의 멀티노드 k3s 클러스터입니다.

## 1. 최종 인프라 구조

```text
A 노드  t3a.xlarge
- k3s server (control-plane, etcd)
- Jenkins
- backend / 운영용 공용 진입점

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

## 3. 네트워크 결론

A 노드는 `172.26.0.0/16` 대역, B/C 노드는 `172.31.0.0/16` 대역에 있어 private IP 기준 직접 통신이 되지 않았습니다.

그래서 다음 결론으로 정리했습니다.

- `A/B/C 모두 server`인 embedded etcd 기반 HA control-plane 구성은 불가
- `A = server`, `B/C = agent` 구조로 확정

즉 현재 클러스터는 멀티노드이지만, 완전한 control-plane HA는 아닙니다.

## 4. 데이터 계층

현재 애플리케이션 데이터 계층은 외부 관리형 서비스를 사용합니다.

- PostgreSQL + PostGIS
- MySQL
- Redis
- S3

서버 쪽은 애플리케이션 실행, 로그/이벤트 저장, 운영 도구, CI/CD에 집중하고, 데이터 저장은 외부 서비스에 맡기는 구조입니다.

## 5. swap

각 노드에는 16GB swap을 설정했습니다.

적용 확인 명령:

```bash
free -h
swapon --show
```

## 6. k3s 구성 상태

최종 확인 기준:

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

현재 노드 라벨은 다음 기준으로 사용합니다.

```text
A: node.smartcane/role=app,      node.smartcane/size=xlarge
B: node.smartcane/role=ops,      node.smartcane/size=xlarge
C: node.smartcane/role=app-lite, node.smartcane/size=large
```

확인:

```bash
sudo kubectl get nodes --show-labels
```

## 8. 방화벽 / 포트

A 노드는 `ufw`를 사용 중이며, B/C public IP 기준으로 필요한 포트만 허용했습니다.

주요 포트:

```text
6443/tcp   Kubernetes API
10250/tcp  kubelet
8472/udp   flannel VXLAN
```

`2379`, `2380`은 HA control-plane을 쓰지 않으므로 현재 최종 구조에서는 필수 포트가 아닙니다.

## 9. Bastion 및 tunnel 스크립트

`infra/aws` 아래의 파일:

- [tunnel-postgres.ps1](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\aws\tunnel-postgres.ps1)
- [tunnel-mysql.ps1](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\aws\tunnel-mysql.ps1)
- [tunnel-redis.ps1](C:\Users\SSAFY\IdeaProjects\S14P31C102\infra\aws\tunnel-redis.ps1)

이 파일들은 로컬 PC에서 bastion을 통해 DB/Redis에 접속할 때 쓰는 보조 스크립트입니다.

즉:

- 애플리케이션 운영에는 필수 아님
- 로컬에서 직접 DB/Redis 확인할 때 유용
- 남겨둬도 되고, 운영 문서상에는 "선택 도구"로 보면 됨

## 10. 현재 판단

현재 인프라는 다음 기준으로 정리하면 가장 정확합니다.

- 온디바이스 AI 기반 구조
- 단일 control-plane + 2 agent 기반 k3s 멀티노드 클러스터
- 외부 관리형 데이터 계층 사용
- Jenkins는 k3s 위에서 운영

## 11. 다음 작업

다음 인프라 작업은 아래 순서로 이어가면 됩니다.

1. Jenkins GitLab 파이프라인 안정화
2. 실제 애플리케이션 소스 기준 Jenkinsfile 확장
3. backend / admin / worker 배포 매니페스트 정리
4. 모니터링(Prometheus / Grafana) 정리
5. README / 발표 자료 최종 동기화
