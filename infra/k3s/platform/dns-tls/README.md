# DNS / TLS

이 디렉터리는 cert-manager, external-dns, TLS 관련 템플릿을 담고 있습니다.

## 현재 상태

- 예전 클러스터에서 HTTPS / cert-manager 구성을 검토했음
- 당시 worker endpoint 통신 불안정 때문에 운영 경로에서 보류했음
- 새 Tailscale 기반 클러스터에서는 아직 DNS/TLS 를 다시 적용하지 않음

즉 이 디렉터리는 현재 `활성 운영 구성` 이 아니라, 이후 재적용을 위한 템플릿이다.

## 포함 파일

- `cluster-issuer-letsencrypt-prod.yaml`
- `external-dns-values.yaml`

## 다시 적용할 때 확인할 것

- 도메인 `k14c102.p.ssafy.io` 의 DNS 제어 권한
- Traefik ingress 동작 확인
- cert-manager webhook / solver 동작 확인
- Tailscale 기반 클러스터에서 worker endpoint 접근 정상 여부

## 권장 적용 순서

1. backend / monitoring / Jenkins 기본 동작 확인
2. ingress 경로 안정화
3. cert-manager 재설치
4. external-dns 적용
5. HTTPS 강제 전환

## 메모

- 지금은 HTTP 기준 서비스 복구와 멀티노드 분산 처리 검증이 우선이다
- TLS는 현재 구조가 안정화된 뒤 다음 단계로 진행한다
