# 같이 가요 (drive-together)

**여러 대의 차로 함께 떠나는 가족이 서로의 위치와 배터리를 보고, 말풍선 요청과 함께 즐기는 놀이로 '따로 달려도 하나의 여행'을 만드는 차량용 그룹 이동 앱**입니다.

같이 이동하는 차들이 하나의 그룹으로 묶여 서로의 위치를 지도 위에서 보고, 휴게소·경로 변경을 제안하면 **모두 동의했을 때 모든 차의 경로가 함께 바뀝니다.**

- 웹 시연(차량 화면 프로토타입): https://olorlo.github.io/drive-together/demo/
- APK: [`apk/따로또같이_같이가요_release.apk`](apk/)

## 현재 구현 범위

| 구분 | 내용 | 상태 |
| --- | --- | --- |
| 차량 앱 (Android) | 지도(MapLibre), 서울 강남 → 강릉 경로 안내(OSRM), 다음 안내 카드, 도착 예정 시각 | 구현 |
| 차량 앱 (Android) | 시뮬레이션 주행(×1 ~ ×120), 실제 위치(GPS) 주행, 경로 이탈 시 재탐색 | 구현 |
| 차량 앱 (Android) | **그룹 시연 모드** (웹 시연과 같은 화면): 그룹 만들기·초대 코드·참여 → 같이 출발, 우리집·큰집·작은집 3대 지도 표시(진행 방향 차 아이콘·이름표), 요청한 차 옆 질문 말풍선, 같이 가는 차 패널(앞/뒤 거리·쉬는 중·배터리/연료), 휴게소 제안·투표, 모두 동의하면 3대 모두 휴게소를 경유지로 넣어 경로 재탐색, 휴게소 휴식·충전, 배터리 25% 이하 충전 제안, 음성 안내(Android TTS) | 구현 (다른 차의 위치·대답은 앱 안에서 모의) |
| 웹 시연 | 그룹 만들기·초대 → 함께 출발 → 휴게소 말풍선 요청과 만장일치 경로 변경 → 배터리 충전 제안 → 같이 듣기 → 벌칙 룰렛 → 도착 | 구현 (그룹 서버·SDK 동작은 페이지 안에서 모의) |
| 그룹 서버 (FastAPI) | 그룹 참여, 위치 공유(WebSocket), 요청·투표, 계획 버전 관리 | 2차 PT 전까지 구현 예정 |
| Pleos SDK 연동 | Vehicle(배터리·연료·주행 상태), NaviHelper(경유지 반영), STT·TTS(음성 요청·응답) | 2차 PT 전까지 구현 예정 |

## 실행 방법

### APK로 바로 실행

1. Pleos Connect 에뮬레이터를 켭니다. (아래 [테스트 환경](#테스트-환경) 참고)
2. APK를 설치합니다. 에뮬레이터 창에 APK 파일을 끌어다 놓거나 다음 명령을 실행합니다.
   ```bash
   adb install -r apk/따로또같이_같이가요_release.apk
   ```
3. 앱 목록에서 **같이 가요**를 실행합니다.
4. 서울 강남 → 강릉 경로와 다음 안내가 보이면 정상입니다.

### 앱 조작

오른쪽 위에서 위치를 어디서 받을지 고릅니다.

| 모드 | 사용법 |
| --- | --- |
| **시뮬레이션** | 아래 바에서 속도(×1 ~ ×120)를 고르고 **주행 시작**. GPS 없이 항상 똑같이 동작합니다. |
| **실제 위치** | 처음 한 번 위치 권한을 허용한 뒤, 에뮬레이터 옆 메뉴 `⋯` → **Location** → **Routes**에서 경로를 재생합니다. (또는 `adb emu geo fix <경도> <위도>`) |

그룹 시연:

1. 앱을 켜면 **어디로 같이 갈까요?** → **그룹 만들고 초대하기** → 큰집·작은집이 초대 코드로 참여 → **3대 같이 출발**
2. 세 차가 각자 출발지(강남·마포·수원)에서 강릉으로 함께 움직입니다. 배속은 하단 바 오른쪽에서 고릅니다.
3. 하단 바의 **휴게소 들르자** → 이유와 휴게소를 고르면 다른 차들이 대답하고, 모두 좋아요면 세 차의 경로가 함께 바뀝니다.
4. 오른쪽 패널의 **시연 · 큰집 요청 받기**를 누르면 큰집 차 옆에 질문 말풍선이 뜹니다. **질문 보기** → **좋아요 / 다음에**로 대답합니다.
5. 도착하면 함께 달린 기록이 나오고, **그룹 끝내고 위치 공유 멈추기**로 그룹을 끝냅니다.

### 소스에서 빌드

- **Android Studio:** **Open** → `drive-together/android` 폴더 선택 → Gradle Sync → 실행 대상에서 Pleos 에뮬레이터 선택 → ▶ Run
- **명령줄 (release APK):**
  ```bash
  cd android
  ./gradlew assembleRelease
  # 결과: android/app/build/outputs/apk/release/app-release.apk
  ```
  release 빌드는 별도 키스토어 없이 설치할 수 있도록 debug 키로 서명됩니다.

자세한 내용은 [android/README.md](android/README.md)를 참고하세요. 그룹 서버 실행 방법은 [server/README.md](server/README.md)에 있습니다.

## 테스트 환경

| 항목 | 내용 |
| --- | --- |
| 에뮬레이터 | **Pleos Connect v2.1.0** (Android 14, API 34, x86_64) · 시스템 이미지 `system-images;android-34;pleos_car_emulator;x86_64` |
| 화면 규격 | 2560 × 1440 (가로, 12.8인치, 160dpi) · 에뮬레이터 기기 정의 `Pleos Connect v2.1.0` |
| 에뮬레이터 설정 | RAM 4GB, 하드웨어 가속(Windows: WHPX), 처음 실행은 Cold boot |
| 에뮬레이터 이미지 주소 | Android Studio → Settings → Android SDK → SDK Update Sites에 추가<br>`https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp-sys-img.xml` |
| 개발 도구 | Android Studio Rabbit 2 (2026.2.2), JDK 25 (Gradle 데몬), Gradle 9.4.1 / AGP 9.2.0 / Kotlin 2.4.0 |
| SDK 버전 | compileSdk 37 / minSdk 28 / targetSdk 34 |
| Pleos SDK | `ai.pleos.playground:Vehicle:2.3.2` |
| 필요한 설정 | 인터넷 연결 (지도 타일: OpenFreeMap, 경로: OSRM 공개 서버) · 실제 위치 모드는 위치 권한 허용 필요 |

## 기술 스택

| 영역 | 스택 |
| --- | --- |
| 차량 앱 | Android (Kotlin, Jetpack Compose), MapLibre, Pleos Vehicle SDK |
| 그룹 서버 | Python, FastAPI, WebSocket |
| 웹 시연 | HTML, JavaScript, MapLibre GL JS |

## 폴더 구조

```
drive-together/
├── android/        # 차량 앱 (Android Studio 프로젝트 루트) → android/README.md
├── server/         # 그룹 서버 (FastAPI) → server/README.md
├── docs/           # 웹 시연(docs/demo), API·메시지 명세, 설계 → docs/README.md
├── apk/            # 공모전 제출용 release APK
└── .github/        # PR·이슈 템플릿
```

## 협업 규칙

브랜치, 커밋, PR 규칙은 [CONTRIBUTING.md](CONTRIBUTING.md)를 참고하세요.

## 팀

팀 **따로또같이** · 현대자동차그룹 차량용 앱 개발 공모전 참가작
