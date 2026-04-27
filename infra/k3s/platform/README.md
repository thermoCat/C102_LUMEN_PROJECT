# SmartCane 플랫폼 고도화 템플릿

이 디렉터리는 현재 동작 중인 Jenkins+k3s 배포 구조 위에 운영 고도화를 추가하기 위한 템플릿을 담고 있습니다.

하위 디렉터리:

- `monitoring/`
  - Prometheus/Grafana 모니터링 구성
- `dns-tls/`
  - cert-manager, external-dns, TLS 발급 템플릿
- `backup/`
  - 리소스 백업 스크립트와 정책 정리
- `secrets/`
  - AWS Secrets Manager + External Secrets 연동 예시
- `gitops/`
  - Argo CD 기반 GitOps 전환 템플릿

의도:

- 현재 Jenkins 직접 배포 구조는 유지
- 운영 안정성을 높이는 기능은 별도 디렉터리에서 단계적으로 도입

추천 적용 순서:

1. DNS 정식 연결
2. TLS/HTTPS 적용
3. 모니터링 구축
4. 백업 정책 정리
5. Secret 관리 고도화
6. Argo CD/GitOps 전환 검토
