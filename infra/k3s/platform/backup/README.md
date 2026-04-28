# 백업 전략

현재 SmartCane 백업 범위는 아래를 기준으로 잡습니다.

- PostgreSQL/MySQL RDS 스냅샷 및 복구 지점 보존
- Redis 스냅샷 정책
- S3 버전 관리 및 lifecycle 정책
- Kubernetes `smartcane` 네임스페이스 리소스 백업

권장 주기:

- RDS 자동 백업: 매일
- Kubernetes 리소스 export: 매일
- 복구 리허설: 주 1회 이상

함께 제공되는 스크립트:

- `export-smartcane-resources.sh`

이 스크립트는 현재 네임스페이스 리소스를 YAML로 내보내 운영 점검과 복구 준비에 활용할 수 있습니다.
