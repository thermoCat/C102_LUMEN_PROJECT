# Secret 관리 고도화

현재 클러스터는 Kubernetes `Secret` 객체를 직접 사용하는 방식으로 동작합니다.

다음 고도화 목표:

- 비밀값의 원본은 AWS Secrets Manager에 저장
- External Secrets Operator로 클러스터에 동기화
- 사람이 직접 `secret.yaml`을 자주 수정하지 않도록 운영 방식 개선

권장 순서:

1. External Secrets Operator 설치
2. AWS Secrets Manager용 `ClusterSecretStore` 생성
3. 애플리케이션별 `ExternalSecret` 생성
4. 기존 수동 secret 관리 절차 축소

이 디렉터리의 YAML은 예시 템플릿이며, 실제 secret 이름과 키 구조는 AWS 쪽 구성에 맞춰 채워야 합니다.
