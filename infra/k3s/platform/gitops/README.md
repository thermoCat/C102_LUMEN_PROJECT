# GitOps

이 디렉터리는 Argo CD 기반 GitOps 전환을 위한 템플릿을 담고 있습니다.

## 현재 상태

- 현재 운영은 GitOps가 아니라 `kubectl apply` / CI 기반 배포다
- backend는 새 Tailscale 기반 멀티노드 클러스터에 수동 복구 완료
- Jenkins도 아직 새 클러스터에 재설치하지 않았기 때문에 GitOps 적용 단계는 아니다

즉 이 디렉터리는 현재 운영 설정이 아니라 향후 확장용 참고 자료다.

## 포함 파일

- `smartcane-project.yaml`
- `backend-application.yaml`

## 나중에 GitOps 로 전환하면 좋아지는 점

- 배포 이력 추적이 쉬움
- 수동 `kubectl apply` 의존도 감소
- 클러스터 상태와 Git 상태를 맞추기 쉬움

## 전환 전 선행 조건

- Jenkins / monitoring 재설치 완료
- backend 운영 매니페스트 정리 완료
- secret 관리 방식 정리
- 실제 운영 이미지 태그 전략 정리

## 메모

- 지금은 GitOps 전환보다 현재 멀티노드 운영 안정화가 우선이다
