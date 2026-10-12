# 5. 파일 관리

## 저장소 두 개
| 저장소 | 담는 것 |
|---|---|
| [practicech/blog](https://github.com/practicech/blog) (소스) | 코드·빌드·배포 설정·CHANGELOG. `main` 머지 = 배포 |
| [AIPKANG/docs](https://github.com/AIPKANG/docs) (문서) | 팀 원본 설계 문서 `docs/`, 기능별 명세 `specs/NNN-이름/`, 이 안내서 `guide/` |

나눈 이유: 문서 수정이 배포를 일으키지 않게, 팀원이 문서만 볼 수 있게, 소스 저장소 기록이 코드 변경만 담게.

## 소스 저장소 구조 (1.14.0, 백엔드·프론트 분리)
```
backend/    Spring Boot 서버
  src/main/java/com/team/blog/<기능 모듈>/   account·post·tag·media·interaction·discovery·notification·moderation·shared + 개인 확장 모듈
     application/(규칙) infra/(DB·Redis·외부 API) web/(주소 처리)
  src/main/resources/  application*.yml, db/migration(V1 공통 ~ V14), policy(금칙어), security(흔한 비밀번호)
  src/test/            통합 테스트(Testcontainers)
frontend/   화면
  src/main/resources/templates/  Thymeleaf 화면(기능별 폴더, fragments/ 공통 조각)
  src/main/resources/static/     css(theme.css 색상 토큰, site.css), js(기능별), fonts
  src/test/js/                   화면 JS 자체 검사(Node)
db/         데모 데이터, MySQL 사본(ERD 도구용)
deploy/     서버 스크립트(school/ = 학교 서버)
.github/    배포 워크플로, PR·이슈 템플릿, CODEOWNERS
```
- `frontend`는 jar로 묶여 `backend` 실행 jar 안에 들어갑니다. 배포 단위는 하나라 서버 설정은 그대로이고, 화면과 서버 코드를 따로 보고 고칠 수 있습니다.
- 색은 `theme.css` 토큰에만 쓰고, 다른 곳에 색을 직접 쓰면 테스트가 실패합니다(다크 모드 일관성).
- DB 변경은 이미 적용된 마이그레이션을 고치지 않고 새 `V__` 파일을 추가합니다. 개인 확장은 공통 테이블을 바꾸지 않고 새 테이블만 추가.

## 비밀값·생성 파일
- `.env`, `.env.*`는 `.gitignore`로 제외, 견본 `.env.example`만 올림. 운영 값은 GitHub Repository secrets
- `build/`, `.gradle/`, IDE 파일, `*.jar`, `design-handoff/`(디자인 의뢰 사본)는 올리지 않음

## 사용자가 올린 사진
- 운영은 서버 도커 볼륨(`team-blog-data`), 로컬은 `build/local-images`. 주소는 서명된 링크
- 1인 1GB·하루 200장 한도. 지운 글의 사진은 정리 작업(매일)이 지우고 공간을 돌려줌
- 휴지통 글은 30일 뒤, 탈퇴는 30일 뒤 익명 처리 작업이 정리
