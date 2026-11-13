# server — 그룹 서버

차량 앱들이 공통으로 접속하는 중계 서버입니다. 각 차의 위치를 다른 차에 전달하고, 휴게소·경로 제안과 투표를 모아 모두 동의하면 새 경로 계획을 모든 차에 보냅니다.

- Python 3.11, FastAPI
- 실시간 통신: FastAPI 기본 WebSocket
- 저장소: 처음엔 메모리(dict)로 시작. 시연 전까지 DB 없이 진행하고, 필요해지면 SQLite 추가

## 실행

```bash
cd server
python -m venv .venv
.venv\Scripts\activate          # Mac/Linux: source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --host 0.0.0.0 --port 8000
```

- 브라우저에서 http://localhost:8000/health → `{"status":"ok"}` 이면 정상
- http://localhost:8000/docs 에서 API 목록 확인·테스트 가능
- Android 에뮬레이터 안에서는 `localhost` 대신 **`10.0.2.2:8000`** 으로 접속 (에뮬레이터 기준 PC 주소)

## 폴더 구조

```
server/
├── app/
│   ├── main.py            # FastAPI 앱 생성, 라우터 등록
│   ├── core/              # 설정(config.py), 공통 예외
│   ├── ws/                # WebSocket 연결 관리, 그룹별 브로드캐스트
│   └── domain/
│       ├── group/         # 그룹 생성·참여·종료, 멤버
│       ├── location/      # 차량 위치·상태 공유
│       ├── proposal/      # 휴게소/경로 제안, 투표, 만장일치 판정
│       ├── plan/          # 경로 계획과 버전 (v1 → v2 …)
│       └── poi/           # 휴게소 시설, 정체 구간 데이터
├── tests/
├── requirements.txt
└── .env.example           # 환경변수 예시. 실제 값은 .env (커밋 금지)
```

각 도메인 폴더에는 아래 파일을 둡니다.

| 파일 | 역할 |
| --- | --- |
| `router.py` | API 주소 정의 (요청 받고 응답 돌려주기) |
| `service.py` | 실제 로직 (투표 집계, 계획 버전 올리기 등) |
| `schemas.py` | 주고받는 데이터 형식 (Pydantic 모델) |

## 패키지 추가 시

`pip install 패키지명` 후 `requirements.txt`에 버전과 함께 한 줄 추가하고 같이 커밋합니다.
