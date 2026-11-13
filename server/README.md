# server — 그룹 서버

이 폴더를 Spring Boot 프로젝트 루트로 사용합니다. (Spring Initializr로 생성 후 이 폴더에 풀기)

- Spring Boot, Gradle(Kotlin DSL 또는 Groovy 중 팀에서 하나로 통일)
- 패키지: `com.drivetogether`
- 실시간 통신: WebSocket + STOMP
- 도메인별 패키지 구조

## 패키지 구조

```
src/main/java/com/drivetogether/
├── global/
│   ├── config/          # WebSocket, CORS, Swagger 설정
│   ├── exception/       # 공통 예외, 에러 코드, 전역 핸들러
│   └── common/          # 공통 응답 포맷
└── domain/
    ├── group/           # 그룹 생성·참여·종료, 멤버
    ├── location/        # 차량 위치·상태 공유 (STOMP 브로드캐스트)
    ├── proposal/        # 휴게소/경로 제안, 투표, 만장일치 판정
    ├── plan/            # 경로 계획과 버전 (v1 → v2 …)
    └── poi/             # 휴게소 시설, 정체 구간 데이터
```

각 도메인 안에는 `controller/`, `service/`, `repository/`, `entity/`, `dto/`를 둡니다.

## 로컬 설정

비밀값이 들어가는 `src/main/resources/application-local.yml`은 커밋하지 않습니다 (`.gitignore` 처리됨).
