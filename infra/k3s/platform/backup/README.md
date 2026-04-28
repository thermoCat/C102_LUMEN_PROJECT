# Backup

이 디렉터리는 SmartCane k3s 운영 환경의 백업 기준을 정리합니다.

## 현재 백업 우선순위

- RDS PostgreSQL / MySQL
- Redis 운영 데이터
- S3 버킷 및 lifecycle 정책
- Kubernetes `smartcane` namespace 리소스
- k3s 재배포에 필요한 secret / values / manifest

## 권장 주기

- RDS 자동 백업: 매일
- k8s 리소스 export: 매일
- 복구 리허설: 주 1회 이상

## 포함 스크립트

- `export-smartcane-resources.sh`

이 스크립트는 현재 namespace 리소스를 YAML로 내보내 백업 또는 복구 준비에 사용한다.

## 새 클러스터 기준으로 꼭 챙길 것

- `smartcane` namespace 리소스 export
- backend용 실제 `secret.yaml`
- monitoring 운영용 values
- Jenkins credential / values / webhook 메모
- Tailscale 기반 노드 역할 문서

## 운영 메모

- 이번 재구성 과정에서 `in-place` 변경보다 `새 클러스터 재구성` 이 더 안전하다는 점이 확인됐다
- 따라서 백업도 단순 데이터뿐 아니라 `재구성 절차` 까지 같이 문서화하는 것이 중요하다
