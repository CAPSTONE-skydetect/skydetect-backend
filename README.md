<div align="center">

# SkyDetect Backend

**SkyDetect 관제 시스템의 Spring Boot API 서버**

인증 · 회원 관리 · 실시간 클립 · 관측 기록

</div>

---

## 개요

SkyDetect는 공항 주변 영상에서 비행 물체를 추적하고, 움직임 궤적으로 드론과 새를 판별하는 시스템입니다.
이 저장소는 관제 화면이 사용하는 API를 제공합니다. 사용자가 실시간 영상에서 물체를 발견하면, 검출 시점 전후 구간을 클립으로 만들고 판정 결과를 기록합니다.

> 단국대학교 SW중심대학사업단 2026 캡스톤 프로젝트이며, **2026 캡스톤 페스티벌 우수상**을 받았습니다.

## 주요 기능

- **세션 쿠키 인증**: Spring Security 기반 로그인 / 로그아웃. HttpOnly 세션 쿠키와 CSRF 토큰을 사용하고, 유휴 30분 · 절대 12시간 만료를 적용합니다. 방식 선택 근거는 [ADR 001](docs/adr/001-session-cookie.md)에 정리했습니다.
- **회원 관리**: 회원가입, 이메일로 아이디 찾기, 비밀번호 재설정(일회용 토큰 + SMTP 메일 발송)
- **실시간 클립**: 검출 요청 시점 기준 -3초 ~ +10초 구간을 클립 추출 워커에 요청하고, 상태(`PENDING` / `READY` / `FAILED`)와 영상 재생(Range 요청 지원)을 제공합니다. → [docs/pi-clips.md](docs/pi-clips.md)
- **관측 기록**: 사용자별 판정 결과와 위치 · 메모를 저장하고 조회 · 삭제합니다.

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| Language | Java 21 |
| Framework | Spring Boot 4, Spring Security, Spring Data JPA, Bean Validation |
| Database | MySQL 8.4 |
| API 문서 | springdoc-openapi (Swagger UI) |
| Build / Test | Gradle, JUnit 5 |
| Infra | Docker, GitHub Actions, GCP (Compute Engine, Cloud SQL, Artifact Registry) |

## 시작하기

### 요구 사항

- JDK 21
- MySQL 8.x (`skydetect` 데이터베이스)

### 로컬 실행

```bash
# 1. 로컬 설정 파일 만들기 (커밋되지 않음)
cp src/main/resources/application-local.yaml.example src/main/resources/application-local.yaml
#    → DB 계정, 최초 운영자 계정 입력

# 2. 실행
./gradlew bootRun
```

서버는 http://localhost:8080 에서 실행되며, 로컬 설정에서 Swagger UI를 켜면 http://localhost:8080/swagger-ui.html 에서 API를 확인할 수 있습니다.

프론트엔드 · AI 서버 · MySQL까지 한 번에 실행하려면 [skydetect-ai의 Docker Compose 구성](https://github.com/CAPSTONE-skydetect/skydetect-ai/tree/dev/docker)을 사용하세요.

### 주요 설정

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `spring.datasource.*` | `localhost:3306/skydetect` | MySQL 접속 정보 |
| `skydetect.auth.initial-operator.*` | – | 최초 기동 시 만들 운영자 계정 |
| `skydetect.auth.absolute-session-timeout` | `12h` | 로그인 기준 절대 만료 |
| `skydetect.auth.frontend-base-url` | `http://localhost:5173` | 비밀번호 재설정 링크의 기준 주소 |
| `skydetect.auth.mail.mode` | `log` | `log`: 메일을 로그로 출력, `smtp`: 실제 발송 |
| `skydetect.clips.worker-url` | – | 클립 추출 워커 주소. 없으면 클립 생성이 503을 반환 |

## API

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/api/auth/login` | 로그인 (form-urlencoded) |
| `POST` | `/api/auth/logout` | 로그아웃 |
| `GET` | `/api/auth/me` | 현재 로그인 사용자 |
| `POST` | `/api/auth/signup` | 회원가입 |
| `POST` | `/api/auth/find-username` | 이메일로 아이디 안내 메일 발송 |
| `POST` | `/api/auth/password-reset/request` | 비밀번호 재설정 메일 발송 |
| `POST` | `/api/auth/password-reset/confirm` | 토큰으로 비밀번호 변경 |
| `POST` | `/api/clips` | 클립 생성 요청 (202) |
| `GET` | `/api/clips` | 내 클립 목록 (최근 100개) |
| `GET` | `/api/clips/{id}` | 클립 상태 |
| `GET` | `/api/clips/{id}/video` | 클립 영상 (Range 지원) |
| `GET` | `/api/observations` | 내 관측 기록 목록 |
| `PUT` | `/api/observations/{id}` | 관측 기록 저장 |
| `DELETE` | `/api/observations/{id}` | 관측 기록 삭제 |
| `DELETE` | `/api/observations` | 관측 기록 전체 삭제 |

상태를 바꾸는 요청(`POST` / `PUT` / `DELETE`)에는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보내야 합니다.

## 프로젝트 구조

```text
src/main/java/com/skydetect/backend
├── auth/            # 로그인, 세션 만료 필터, Security 설정, 초기 운영자 생성
│   └── account/     # 회원가입, 아이디 찾기, 비밀번호 재설정, 메일 발송
├── user/            # 회원 엔티티
├── clips/           # 실시간 클립 작업과 영상 제공
├── observations/    # 사용자별 관측 기록
└── config/          # OpenAPI 설정
```

## 테스트

```bash
./gradlew test
```

통합 테스트는 실제 MySQL에 연결합니다. CI에서는 GitHub Actions가 MySQL 8.4 컨테이너를 띄워 실행합니다.

## 배포

`main`에 push 되면 GitHub Actions가 테스트 후 Docker 이미지를 Artifact Registry에 올리고, 앱 VM이 1분 안에 새 이미지를 받아 백엔드를 재시작합니다. GCP 인증은 Workload Identity Federation을 사용해 저장소에 키 파일을 두지 않습니다.

전체 배포 구성은 [skydetect-ai/docker/gcp](https://github.com/CAPSTONE-skydetect/skydetect-ai/tree/dev/docker/gcp)를 참고하세요.

## 관련 저장소

| 저장소 | 역할 |
| --- | --- |
| [skydetect-ai](https://github.com/CAPSTONE-skydetect/skydetect-ai) | 추적 · 분류 AI 서버, 전체 실행 · 배포 구성 |
| [skydetect-frontend](https://github.com/CAPSTONE-skydetect/skydetect-frontend) | Vue 3 관제 화면 |
