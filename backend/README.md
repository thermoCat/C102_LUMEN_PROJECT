# SmartCane Backend

Minimal Spring Boot backend for SmartCane infrastructure validation.

## Stack

- Java 17
- Spring Boot 3.4.1
- Spring Web
- Spring Boot Actuator

## Endpoints

- `GET /actuator/health`
- `GET /actuator/health/readiness`
- `GET /actuator/health/liveness`

## 기본 설정 키

현재 `application.properties`에는 실제 연결 구현 전 단계에서 사용할 기본 설정 형태를 미리 잡아두었습니다.

- PostgreSQL RDS
- MySQL RDS
- Redis ElastiCache
- AWS Region / S3
- JWT Secret

실제 값은 코드에 하드코딩하지 않고 환경변수, Jenkins credential, Kubernetes Secret/ConfigMap으로 주입하는 전제를 사용합니다.

## Purpose

This backend is a small starter application used to move the current pipeline
from bootstrap CI toward real build and deployment flow.

It is intended to validate:

- Jenkins checkout and application-level CI
- Docker image packaging
- k3s Deployment / Service / Ingress
- webhook-driven delivery flow

## Local Build

This project uses the Gradle Wrapper with Gradle 8.7.

```bash
./gradlew bootJar
```

## Docker Build

Build from the `backend` directory:

```bash
docker build -t smartcane/backend-api:local .
```

## Next Step

To complete real CD, we still need:

- Jenkins runtime that can execute `./backend/gradlew`
- image registry credentials
- deployment image replacement strategy
- namespace/secret values for the target cluster
