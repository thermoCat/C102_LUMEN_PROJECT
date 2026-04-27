# Argo CD / GitOps 전환 준비

현재 배포는 Jenkins가 직접 `kubectl apply` 하는 방식입니다.

이 디렉터리는 다음 단계인 GitOps 구조로 전환하기 위한 Argo CD 템플릿을 제공합니다.

권장 전환 방식:

1. Jenkins는 계속 이미지 빌드/푸시 담당
2. 배포 책임은 Argo CD로 이동
3. Jenkins는 이미지 태그 변경만 Git에 반영
4. Argo CD가 Git 상태를 클러스터에 동기화

Argo CD 설치 후 예시 적용:

```bash
kubectl apply -f infra/k3s/platform/gitops/smartcane-project.yaml
kubectl apply -f infra/k3s/platform/gitops/backend-application.yaml
```

의도:

- 배포 이력 가시성 향상
- 수동 `kubectl` 의존도 감소
- 선언적 운영 방식으로 전환
