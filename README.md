# SKALA Rooms

교육생들이 회의실의 사용 여부를 확인하고 직접 예약할 수 있도록 만든 **Spring Boot 기반 회의실 예약 서비스**입니다.

관리자 승인 없이 예약할 수 있으며, 이용 전·후 사진 인증을 통해 회의실을 자율적으로 관리하는 흐름을 구현했습니다.

> 포트폴리오용 공개 저장소입니다. 실제 회원 정보, 예약 데이터, 인증 사진, 데이터베이스 접속 정보 및 가입용 접근 코드는 포함하지 않습니다.

## 문제 정의

- 회의실 사용 여부를 확인하려면 직접 방문하거나 다른 교육생에게 물어봐야 했습니다.
- 구두로 일정을 공유하면 예약 시간이 겹치기 쉽고, 남은 시간을 한눈에 확인하기 어려웠습니다.
- 별도 관리 인력 없이도 이용자가 회의실 상태를 함께 관리할 방법이 필요했습니다.

## 핵심 기능

### 계정과 접근 제어

- `U000` 형식의 교육생 번호로 회원가입 및 로그인
- 교육생 번호 중복 가입 방지
- 영문과 특수문자를 포함한 8자 이상 비밀번호 정책
- scrypt 기반 비밀번호 해시 저장
- 선택적으로 가입용 접근 코드를 설정해 공개 서버의 무단 가입 제한

### 예약

- 201호·202호의 날짜별 예약 현황과 빈 시간 조회
- 09:00~18:00 사이를 30분 단위로 선택해 예약
- 한 사람당 하루 최대 3시간 제한
- 회의실별 최대 12명 제한
- 예약 시작 전 본인 예약 취소
- 다른 사용자의 예약 시간은 공유하되 개인정보와 예약 목적은 노출하지 않음

### 이용 인증과 기록

- 예약 시작 5분 전부터 이용 전 사진 인증
- 예약 종료 후 이용 후 사진 인증
- JPEG, PNG, WebP 파일 형식과 파일 시그니처 검증
- 본인만 인증 사진을 조회할 수 있도록 접근 제어
- 개인별 예약 내역, 인증 상태, 누적 이용 시간 조회

## 화면 흐름

```text
회원가입/로그인
      ↓
날짜별 회의실 현황 확인
      ↓
연속된 30분 시간대 선택 및 예약
      ↓
내 예약에서 이용 전·후 사진 인증
      ↓
나의 예약 내역과 누적 이용 기록 확인
```

## 기술 스택

| 영역 | 기술 |
| --- | --- |
| Backend | Java 21, Spring Boot 4.1, Spring MVC |
| View | Thymeleaf, HTML, CSS, JavaScript |
| Security | Spring Security, scrypt |
| Database | PostgreSQL 16, Spring JDBC |
| Migration | Flyway |
| Test | JUnit 5, Mockito, Spring Security Test |
| Build/Deploy | Maven, Docker, Railway |

## 주요 설계

### 중복 예약 방지

애플리케이션의 사전 확인만으로는 동시에 들어오는 예약 요청을 완전히 막을 수 없습니다. PostgreSQL의 exclusion constraint를 사용해 같은 회의실의 시간이 겹치는 예약을 데이터베이스 수준에서 차단했습니다.

```sql
EXCLUDE USING gist (
  room_id WITH =,
  tstzrange(starts_at, ends_at, '[)') WITH &&
) WHERE (status = 'approved')
```

### 일일 예약 한도 동시성 제어

동일 사용자가 동시에 여러 요청을 보내 하루 3시간 제한을 초과하는 상황을 막기 위해 사용자·날짜 조합에 PostgreSQL advisory transaction lock을 적용했습니다. 잠금 안에서 기존 사용 시간을 합산하고 새 예약을 생성합니다.

### 사진 데이터 보호

- 업로드 크기는 최대 5MB로 제한합니다.
- MIME 타입뿐 아니라 이미지 파일 시그니처도 함께 검증합니다.
- 사진은 PostgreSQL `BYTEA`에 저장하며 예약 소유자가 인증된 경우에만 반환합니다.
- 오류 응답에서 내부 메시지와 스택 트레이스를 노출하지 않습니다.

## 프로젝트 구조

```text
src/main/java/com/skala/rooms
├── auth          # 로그인, 비밀번호 정책과 계정 서비스
├── config        # Spring Security 설정
├── member        # 회원 도메인과 DB 접근
├── reservation   # 예약 규칙, 동시성 제어와 사진 인증
├── room          # 회의실 도메인과 DB 접근
└── web           # MVC Controller와 View Model

src/main/resources
├── db/migration  # Flyway 스키마 마이그레이션
├── static        # CSS, JavaScript, 이미지
└── templates     # Thymeleaf HTML
```

## 로컬 실행

### 요구 사항

- Java 21
- Docker

### 1. PostgreSQL 실행

로컬 PostgreSQL 16을 준비하고 빈 데이터베이스를 생성합니다. 아래는 Docker 예시입니다.

```bash
docker run --name skala-rooms-postgres \
  -e POSTGRES_DB=skala_rooms \
  -e POSTGRES_USER=skala_rooms \
  -e POSTGRES_PASSWORD=local_password \
  -p 5433:5432 \
  -d postgres:16
```

### 2. 환경변수 설정

`.env.example`을 참고해 현재 터미널에 값을 설정합니다. `.env` 파일은 Git에 포함되지 않습니다.

```bash
export DB_URL='jdbc:postgresql://localhost:5433/skala_rooms'
export DB_USERNAME='skala_rooms'
export DB_PASSWORD='local_password'
export SIGNUP_ACCESS_CODE='local-access-code'
```

### 3. 애플리케이션 실행

```bash
./mvnw spring-boot:run
```

브라우저에서 `http://localhost:8080`에 접속합니다. Flyway가 최초 실행 시 테이블과 201호·202호 기본 데이터를 생성합니다.

## 테스트와 빌드

```bash
./mvnw test
./mvnw clean package
```

빌드 결과는 `target/skala-rooms-0.1.0.jar`에 생성됩니다.

## Docker 실행

```bash
docker build -t skala-rooms:0.1.0 .
docker run --rm -p 8080:8080 \
  -e DB_URL="$DB_URL" \
  -e DB_USERNAME="$DB_USERNAME" \
  -e DB_PASSWORD="$DB_PASSWORD" \
  -e SIGNUP_ACCESS_CODE="$SIGNUP_ACCESS_CODE" \
  skala-rooms:0.1.0
```

## 배포 환경변수

| 이름 | 필수 | 설명 |
| --- | --- | --- |
| `DB_URL` | 예 | JDBC PostgreSQL 접속 주소 |
| `DB_USERNAME` | 예 | PostgreSQL 사용자 |
| `DB_PASSWORD` | 예 | PostgreSQL 비밀번호 |
| `PORT` | 아니요 | 서버 포트, 기본값 `8080` |
| `COOKIE_SECURE` | 권장 | HTTPS 환경에서는 `true` |
| `THYMELEAF_CACHE` | 권장 | 운영 환경에서는 `true` |
| `SIGNUP_ACCESS_CODE` | 권장 | 회원가입 시 요구할 비밀 접근 코드 |

실제 값은 소스나 `.env`에 커밋하지 않고 배포 서비스의 Secret/Variable 기능으로 관리합니다.

## 데이터베이스 구성

- `members`: 회원과 비밀번호 해시
- `rooms`: 회의실 정보
- `reservations`: 예약 시간, 목적, 인원과 상태
- `reservation_photos`: 이용 전·후 인증 이미지

회원·예약·사진의 실제 운영 데이터는 저장소에 포함되지 않습니다.

## 개선 계획

- 캘린더/주간 시간표 기반 예약 현황 UI
- 학교 계정 또는 소셜 로그인을 활용한 가입자 검증
- 인증 사진의 오브젝트 스토리지 분리
- 관리자용 회의실·이용 정책 설정 화면

## 주의 사항

이 프로젝트는 교육 환경에서 제작한 비공식 포트폴리오 프로젝트이며, 특정 기관의 공식 서비스가 아닙니다. 저장소의 로고와 명칭을 외부 서비스에 사용할 때에는 해당 권리와 사용 정책을 별도로 확인해야 합니다.
