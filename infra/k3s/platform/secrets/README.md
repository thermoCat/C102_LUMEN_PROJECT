# Secrets

이 디렉터리는 AWS Secrets Manager 와 Kubernetes Secret 연동용 템플릿을 담고 있습니다.

## 현재 상태

- backend는 현재 `secret.yaml` 을 직접 적용해서 복구한 상태다
- External Secrets 기반 자동화는 아직 새 클러스터에 재적용하지 않음
- 따라서 현재 이 디렉터리는 `운영 중 구성` 이 아니라 `향후 전환용 템플릿` 이다

## 포함 파일

- `clustersecretstore-aws-secretsmanager.example.yaml`
- `backend-external-secret.example.yaml`

## 현재 실제 운영 방식

- A control-plane 에서 실제 `secret.yaml` 을 직접 적용
- backend pod는 `backend-secret` 을 envFrom 으로 읽음

## 나중에 External Secrets 로 전환하면 좋은 점

- secret 값 Git 분리
- 운영 secret 갱신 자동화
- 신규 클러스터 복구 시 반복 작업 감소

## 전환 전 확인할 것

- AWS IAM / IRSA 또는 접근 권한 전략
- 새 k3s 클러스터에서 External Secrets Controller 설치 위치
- `smartcane` namespace 대상 secret 이름 정합성

## 메모

- 지금은 멀티노드 복구 직후라서 단순한 직접 secret 적용 방식이 더 빠르고 안정적이다
