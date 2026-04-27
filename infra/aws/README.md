# SmartCane AWS 인프라 정리

이 문서는 현재 구축된 SmartCane AWS/k3s 인프라 구성을 정리한 문서입니다.

현재 구조:

- A 노드: k3s server(control-plane), Jenkins
- B 노드: k3s agent
- C 노드: k3s agent

핵심 특징:

- 단일 control-plane + 2 worker 구조
- 외부 관리형 데이터 계층 사용
  - PostgreSQL(RDS, PostGIS)
  - MySQL(RDS)
  - Redis(ElastiCache)
  - S3

현재 인프라 상태:

- 멀티노드 k3s 클러스터 구성 완료
- Jenkins on k3s 운영 중
- backend CI/CD 자동 배포 완료
- Traefik ingress 라우팅 정상 동작

참고 스크립트:

- `tunnel-postgres.ps1`
- `tunnel-mysql.ps1`
- `tunnel-redis.ps1`

위 스크립트는 로컬 PC에서 bastion 또는 중계 호스트를 통해 외부 DB/Redis에 접속할 때 사용하는 보조 도구입니다.
