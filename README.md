# FMI-BE (Find My Item - Backend)

Spring Boot 기반 분실물/습득물 서비스 백엔드. HTTP API, WebSocket(STOMP), JPA, Flyway, Actuator, Docker, GitHub Actions(GHCR 빌드/푸시, 수동 실행) 기본 세팅을 포함합니다.

## 요구사항
- Java 21 (Temurin 권장)
- Docker / Docker Compose (로컬 MySQL)
- Git

## 빠른 시작
1) 레포 클론
```
git clone https://github.com/find-my-item/FMI-BE.git
cd FMI-BE
```

2) 로컬 MySQL 기동
```
docker compose up -d
```
- 접속 정보: `localhost:3306`
- DB/계정/비밀번호: `fmi / fmi / fmi`

3) 애플리케이션 실행 (기본 프로필: local-mysql)
```
./gradlew bootRun
```
- 첫 실행 시 Flyway가 자동으로 스키마(`users`, `items`)를 생성합니다.
- 헬스체크: `GET http://localhost:8080/api/health` 또는 `GET http://localhost:8080/actuator/health`

## 환경 설정
- 공통: `src/main/resources/application.yml`
  - `spring.profiles.active: local-mysql` (기본 활성 프로필)
  - JPA: `open-in-view: false`, `ddl-auto: none` (스키마는 Flyway가 관리)
  - Flyway: `locations: classpath:db/migration`
  - Actuator: `health`, `info` 노출, liveness/readiness 프로브 활성화
- 로컬 MySQL: `src/main/resources/application-local-mysql.yml`

## 데이터베이스 & 마이그레이션(Flyway)
- 위치: `src/main/resources/db/migration/`
- 규칙: `V{번호}__{설명}.sql` (예: `V1__init.sql`)
- 동작: 애플리케이션 부팅 시 아직 적용되지 않은 버전이 순서대로 실행되고, `flyway_schema_history` 테이블로 이력이 관리됩니다.
- 팀 작업 절차:
  1) 스키마 변경 → 새 파일 추가 (`V2__add_comment_table.sql` 등)
  2) 로컬에서 실행/검증 → PR로 리뷰
  3) 배포 시 애플리케이션 부팅과 함께 자동 적용

## WebSocket(STOMP)
- 엔드포인트: `/ws` (SockJS 지원)
- 프리픽스: 애플리케이션 `/app`, 브로커 `/topic`, `/queue`, 유저 `/user`
- 예시 흐름(테스트 채팅):
  - 클라이언트 구독: `/topic/room.123`
  - 클라이언트 전송: `/app/room/123` 본문 `{ sender, content, timestamp }`
  - 서버 브로드캐스트: `/topic/room.123`

## 보안(Security)
- CSRF 비활성, CORS 기본
- 세션: Stateless
- 공개 엔드포인트: `/actuator/health`, `/actuator/info`, `/ws/**`, `/error`, `/api/health`
- 추후: JWT/OAuth2 인증/인가 확장 예정

## 빌드/JAR/Docker 이미지
- 로컬 빌드
```
./gradlew clean build -x test
```
- 실행 JAR: `build/libs/*.jar`
- Docker 이미지 빌드(로컬)
```
docker build -t fmi-be:local .
```

## GitHub Actions (수동 실행)
- 워크플로: `.github/workflows/docker-ghcr.yml`
- 트리거: 수동(`workflow_dispatch`)만
- 레지스트리: `ghcr.io/find-my-item/fmi-be`
- 태그: `latest`, `sha-<commit>`
- 사용 방법: GitHub → Actions → "Build and Push Docker Image" → Run workflow

## 선택: Kubernetes / Argo CD
- 매니페스트: `k8s/deployment.yaml`, `k8s/service.yaml`
- Argo CD Application: `argocd/application.yaml`
- 주의: 레포에 파일만 있는 상태로는 비용/배포 없음. 실제 배포하려면 K8s 클러스터와 Argo CD 설치 후 동기화 필요.

## 트러블슈팅
- JDK 미설치/버전 불일치: Temurin 21 설치 후 `JAVA_HOME` 설정
- MySQL 접속 실패: `docker compose ps`로 컨테이너 상태 확인, 포트/계정 확인
- CRLF/권한 문제: Dockerfile에서 `gradlew` 권한 및 줄바꿈 처리

## 라이선스
- 내부 프로젝트(비공개) 기준. 필요 시 라이선스 명시.

## Git 브랜치 전략 (Git Branch Strategy)

기본 원칙
모든 작업은 이슈 기반으로 브랜치를 생성하여 진행합니다.
main 브랜치에 직접적인 commit이나 push는 금지합니다.
모든 merge는 Pull Request (PR)를 통해서만 진행합니다.

브랜치 명명 규칙
브랜치 이름은 [이슈번호] 형식으로 생성합니다.
예시: FIND-001

작업 흐름
main 브랜치에서 git pull을 통해 최신 상태를 유지합니다.
main 브랜치로부터 아래 명령어로 새로운 작업 브랜치를 생성합니다.
git checkout -b FIND-001

기능 개발을 완료한 후 commit과 push를 진행합니다.
commit 내용은 {feat: {작업 내용}} 양식에 맞춰 push 진행
GitHub에서 develop 브랜치로 향하는 Pull Request를 생성하고 코드 리뷰를 요청합니다.

의존성이 있는 브랜치 작업
만약 새로운 이슈(FIND-002)가 기존 브랜치(FIND-001)의 작업물을 필요로 한다면,
main이 아닌 ART-001 브랜치에서 FIND-002 브랜치를 생성합니다.
