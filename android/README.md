# android — 차량 앱

이 폴더를 Android Studio 프로젝트 루트로 사용합니다. (New Project 생성 위치를 `drive-together/android`로 지정)

- 언어/UI: Kotlin, Jetpack Compose
- 패키지: `com.drivetogether`
- 단일 `app` 모듈 + 기능별(feature) 패키지 구조

## 패키지 구조

```
app/src/main/java/com/drivetogether/
├── core/
│   ├── designsystem/    # 색상, 타이포, 공통 컴포넌트 (버튼, 말풍선, 바텀시트)
│   ├── network/         # Retrofit, WebSocket(STOMP) 클라이언트 설정
│   └── util/
├── data/
│   ├── remote/          # REST API, WebSocket 메시지 DTO
│   ├── vehicle/         # Vehicle SDK 래퍼 (배터리, 연료, 주행 상태)
│   └── repository/      # Repository 구현체
├── domain/
│   ├── model/           # Group, Member, Proposal, Vote, RoutePlan …
│   └── repository/      # Repository 인터페이스
├── feature/
│   ├── group/           # 그룹 만들기·참여, 같이 가는 차 패널
│   ├── navigation/      # 지도, 경로선, 차량 마커, 다음 안내
│   ├── proposal/        # 휴게소/경로 제안 시트, 투표 채팅, 말풍선
│   └── arrival/         # 도착, 그룹 끝내기
└── MainActivity.kt
```

각 `feature/xxx/` 안에는 `XxxScreen.kt`, `XxxViewModel.kt`, `XxxUiState.kt`를 둡니다.
