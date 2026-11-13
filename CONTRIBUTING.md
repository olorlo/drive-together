# Git 컨벤션

## 1. 브랜치 전략

```
main      ← 제출·시연용 안정 버전만. 직접 push 금지
develop   ← 개발 통합 브랜치. 모든 작업 PR은 여기로. 직접 push 금지
feature/… ← 작업 브랜치. develop에서 분기 → develop으로 PR
```

### 브랜치 이름

```
{type}/#{이슈번호}-{영역}-{작업내용}
```

- `type`: `feature`, `fix`, `refactor`, `docs`, `chore` … (커밋 타입과 같되, 새 기능은 브랜치만 `feature`, 커밋은 `feat`)
- `영역`: `android` | `server` | `docs` | `infra`
- `작업내용`: 영어 소문자, 단어는 `-`로 연결

```
feature/#12-android-vote-chat
feature/#15-server-proposal-api
fix/#21-android-route-redraw
docs/#3-docs-websocket-spec
```

### 작업 흐름

1. 이슈를 만든다 (템플릿 사용, 담당자·라벨 지정)
2. 최신 `develop`에서 브랜치를 딴다
   ```bash
   git switch develop
   git pull origin develop
   git switch -c feature/#12-android-vote-chat
   ```
3. 작업하고 커밋한다 (아래 커밋 규칙)
4. push하고 `develop`으로 PR을 연다
5. 팀원 **1명 이상 승인** 후 **Squash and merge**
6. 머지된 브랜치는 삭제한다

PR 올리기 전 `develop`이 앞서 있으면 rebase로 맞춘다.

```bash
git fetch origin
git rebase origin/develop
git push --force-with-lease
```

### main 반영 (릴리즈)

예선 제출, 중간 시연, 최종 제출처럼 마일스톤마다 `develop` → `main` PR을 **Merge commit**으로 머지하고 태그를 단다.

```bash
git tag -a v0.1.0 -m "예선 제출"
git push origin v0.1.0
```

## 2. 커밋 메시지

```
{type}({영역}): {제목} (#{이슈번호})

{본문 - 선택. 무엇을, 왜 바꿨는지}
```

```
feat(android): 휴게소 제안 바텀시트 추가 (#12)
fix(server): 모두 동의 전 경로가 바뀌던 문제 수정 (#21)
refactor(android): 차량 마커 렌더링 로직 분리 (#30)
docs: WebSocket 메시지 명세 추가 (#3)
```

| type | 언제 |
| --- | --- |
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 동작 변화 없는 코드 구조 개선 |
| `style` | 포맷팅, 세미콜론 등 (로직 변화 없음) |
| `test` | 테스트 추가·수정 |
| `docs` | 문서 |
| `chore` | 빌드 설정, 의존성, 기타 잡일 |
| `ci` | GitHub Actions 등 CI 설정 |

- 영역(scope): `android`, `server`, `infra` (양쪽 다 걸치거나 문서면 생략 가능)
- 제목은 한국어 OK, 50자 이내, 마침표 없이, "~추가 / ~수정 / ~제거"처럼 끝낸다
- 한 커밋에는 한 가지 변경만 담는다

## 3. PR

- 제목은 커밋 메시지 형식과 같게: `feat(android): 투표 채팅 UI 추가 (#12)`
- 본문은 PR 템플릿을 채운다. UI 변경은 **스크린샷 필수**
- PR 하나는 리뷰 가능한 크기로 (대략 변경 400줄 이하 권장)
- 리뷰어는 작업 영역과 상관없이 1명 이상 지정
- 리뷰 코멘트 접두어로 강도를 표시한다
  - `[필수]` 반영해야 머지 가능
  - `[제안]` 반영 여부는 작성자 판단
  - `[질문]` 궁금한 점

## 4. 이슈

- 기능·버그는 이슈 템플릿으로 만든다
- 라벨: `android` `server` `docs` `infra` / `feat` `fix` `refactor` / `priority: high`
- 제목: `[영역] 작업 내용` 예) `[android] 휴게소 제안 바텀시트`

## 5. 기타

- 비밀값(API 키, 키스토어, `local.properties`, `application-local.yml`)은 절대 커밋하지 않는다. 필요하면 팀 채널로 공유
- 줄바꿈은 `.gitattributes`로 LF 통일 (Windows 사용자도 그대로 두면 됨)
- 들여쓰기 등 기본 포맷은 `.editorconfig`를 따른다
