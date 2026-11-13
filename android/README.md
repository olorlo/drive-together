# android — 차량 앱

## 실행 방법

1. Android Studio → **Open** → `drive-together/android` 폴더 선택 (저장소 루트가 아니라 `android` 폴더)
2. 처음 열면 Gradle Sync가 자동으로 돌아요 (몇 분 걸림)
3. Device Manager에서 **Pleos Connect** 에뮬레이터 실행 (Cold boot, RAM 4GB)
4. 위쪽 실행 대상에서 에뮬레이터 선택 → ▶ Run

화면에 "연결됨 ✅ · 누적 주행거리"가 보이면 Vehicle SDK 연동까지 정상입니다.

## 개발 환경

| 항목 | 버전 |
| --- | --- |
| Android Studio | Rabbit 2 (2026.2.2) — 팀원 모두 같은 버전 사용 |
| Gradle / AGP / Kotlin | 9.4.1 / 9.2.0 / 2.4.0 (`gradle/libs.versions.toml`) |
| compileSdk / minSdk / targetSdk | 37 / 28 / 34 |
| Pleos Vehicle SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| 에뮬레이터 | Pleos Connect (API 34, `pleos_car_emulator`) |

Pleos 에뮬레이터 이미지 SDK Update Site (Windows):
`https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml`

라이브러리 버전은 `gradle/libs.versions.toml` 한 곳에서만 관리합니다. Android Studio가 AGP/Gradle 업그레이드를 제안해도 팀에서 합의하기 전엔 누르지 마세요.

- 언어/UI: Kotlin, Jetpack Compose
- 패키지: `com.gachigayo`
- 단일 `app` 모듈 + 기능별(feature) 패키지 구조

## 패키지 구조

```
app/src/main/java/com/gachigayo/
├── core/
│   ├── designsystem/    # 색상, 타이포, 공통 컴포넌트 (버튼, 말풍선, 바텀시트)
│   ├── network/         # Retrofit, WebSocket(OkHttp) 클라이언트 설정
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
