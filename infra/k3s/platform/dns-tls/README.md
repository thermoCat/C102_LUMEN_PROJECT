# DNS 및 TLS

이 디렉터리는 SmartCane 서비스의 호스트 연결과 HTTPS 발급을 위한 템플릿을 담고 있습니다.

적용 순서:

1. cert-manager 설치
2. cert-manager 설치 후 TLS 발급 확인
3. 외부 DNS를 직접 관리하는 구조가 아니라면 `external-dns`는 생략
4. ClusterIssuer 적용
5. 현재는 `k14c102.p.ssafy.io` 를 ingress 주소로 연결

예시 적용:

```bash
kubectl apply -f infra/k3s/platform/dns-tls/cluster-issuer-letsencrypt-prod.yaml
```

현재 backend ingress는 이미 아래 설정을 포함합니다.

- `cert-manager.io/cluster-issuer: letsencrypt-prod`
- `secretName: backend-api-tls`

즉 cert-manager와 DNS만 준비되면, 추가 코드 변경 없이 HTTPS 발급까지 이어질 수 있습니다.

참고:

- 현재 팀이 실제로 사용하는 호스트는 `k14c102.p.ssafy.io` 입니다.
- 별도 구매 도메인이 없으므로 `external-dns`는 기본 전제에서 제외해도 됩니다.
- DNS를 우리가 직접 관리할 수 없는 경우에는 호스트 값만 ingress와 인증서 기준으로 맞추면 됩니다.
