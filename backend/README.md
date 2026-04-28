# SmartCane 백엔드

SmartCane 프로젝트의 Spring Boot 백엔드 애플리케이션입니다.

기술 스택:

- Java 17
- Spring Boot 3.4.1
- Spring Web
- Spring Boot Actuator
- JDBC
- Redis
- AWS SDK for S3

주요 헬스 체크 엔드포인트:

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/health/liveness`

설정 방식:

- 공통 설정 구조는 `application.properties`에 정의
- 실제 값은 환경변수, Kubernetes ConfigMap/Secret, Jenkins credential로 주입

현재 준비된 외부 연동:

- PostgreSQL RDS
- MySQL RDS
- Redis ElastiCache
- AWS S3
- JWT Secret

로컬 실행 예시:

```bash
./gradlew bootJar
```

테스트 실행:

```bash
./gradlew test
```

목적:

- 현재는 인프라 및 배포 검증이 가능한 최소 백엔드 상태
- 이후 실제 API, DB 로직, Redis/S3 사용 기능을 순차적으로 추가 예정
