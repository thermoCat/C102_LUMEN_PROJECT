# DNS 및 TLS

이 디렉터리는 SmartCane 서비스의 정식 도메인 연결과 HTTPS 발급을 위한 템플릿을 담고 있습니다.

적용 순서:

1. cert-manager 설치
2. external-dns 설치
3. DNS 제공자 자격증명 설정
4. ClusterIssuer 적용
5. `api.smartcane.com` 을 ingress 주소로 연결

예시 적용:

```bash
kubectl apply -f infra/k3s/platform/dns-tls/cluster-issuer-letsencrypt-prod.yaml
```

현재 backend ingress는 이미 아래 설정을 포함합니다.

- `cert-manager.io/cluster-issuer: letsencrypt-prod`
- `secretName: backend-api-tls`

즉 cert-manager와 DNS만 준비되면, 추가 코드 변경 없이 HTTPS 발급까지 이어질 수 있습니다.
