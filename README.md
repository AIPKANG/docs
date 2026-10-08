# 팀 블로그 플랫폼

여러 사람이 각자 블로그를 갖고 글을 쓰는 서비스입니다(티스토리·벨로그 같은 형태).
기능은 [GitHub Spec Kit](https://github.com/github/spec-kit)으로 명세(`specs/`)를 먼저 쓰고 그대로 구현했으며, 명세 001~024(팀 공통 최소 요구사항, v1.0.0)가 모두 구현돼 있습니다.
**명세 025부터는 강성찬 개인 확장**입니다(팀은 공통만 맞추고 각자 추가만 하기로 함). 변경 내역은 [`CHANGELOG.md`](CHANGELOG.md).

## 할 수 있는 것

### 팀 공통 최소 요구사항 (v1.0.0, 명세 001~024)

| 영역 | 기능 (명세 번호) |
|---|---|
| 회원 | 이메일 가입·인증, Google·GitHub 로그인(001), 블로그 주소·닉네임(002), 프로필·설정(003), 탈퇴·30일 안 복구(023) |
| 글쓰기 | 자동 저장·충돌 비교(004), 발행·다시 발행(005), 공개/비공개(006), Markdown 정화(007), 사진 올리기(008), 태그(013), AI 태그 추천(021) |
| 읽기 | 전체 글·개인 블로그(009), 글 상세(010), 내 글 관리·휴지통(011), 트렌딩(019), 검색(020), 다크 모드(024) |
| 반응 | 댓글·답글(014), 좋아요(015), 조회수(016), 팔로우·피드(018), 인앱 알림(017) |
| 운영 | 접근 권한 공통 규칙(012), 신고·관리자 숨김·회원 정지(022), 개인정보 처리방침(`/privacy`), sitemap·robots |

### 강성찬 개인 확장 (명세 025~036)

팀은 공통만 맞추고 각자 **추가만** 하기로 했다(docs/01 §2-4). 아래는 강성찬 개인 확장이며 공통 완료 기준은 그대로 통과한다.

| 명세 | 기능 | 끄는 설정 |
|---|---|---|
| 025 | 친구 공개 — 친구 요청·수락(거절은 상대에게 안 알림), 친구만 보는 글 | — |
| 026 | 공개 전환 — 공개로 넓힐 때 확인, 친구에게 첫 공개 응원 알림 | — |
| 027 | 조회수 30분 5회 | `blog.view.*` |
| 028 | 답글 무제한 깊이(3단계 뒤 접기) | `blog.comment.max-depth: 1` |
| 029 | 링크 공개 — 주소의 열쇠를 아는 사람만 | `blog.share.link.enabled` |
| 030 | 그룹 공개 — 친구 그룹에만 보이는 글 | `blog.friend.groups.enabled` |
| 031 | 잔디·스트릭 — 블로그 1년 기록 칸 | `blog.activity.enabled` |
| 032 | 작성자 통계 — `/manage/stats` | `blog.stats.enabled` |
| 033 | 짧은 기록 — 280자 한 줄 기록 | `blog.notes.enabled` |
| 034 | 같은 주제로 쓰기·릴레이 — `/missions` | `blog.missions.enabled` |
| 035 | AI 티저 — 카드 한 줄 소개 | `blog.teaser.enabled` |
| 036 | 홈 "새로 온 작가" | `blog.home.newcomers.enabled` |

그 밖에 디자인 시안(크림·세이지, 사진 배너, 한 줄 4개 카드·한 쪽 12개)도 강성찬 개인 확장으로 적용했다. 변경 내역: [`CHANGELOG.md`](CHANGELOG.md).

## 기술 구성

- **앱**: Java 21, Spring Boot 4.1, Gradle(Kotlin DSL). 화면은 Thymeleaf 서버 렌더링 + 작은 순수 JS, 세션 로그인(Spring Session·Redis)
- **데이터**: PostgreSQL 18(스키마는 `V1__common_schema.sql` 하나, Flyway), Redis(자동 저장 버퍼·요청 제한·조회수·트렌딩·세션)
- **사진**: 처음에는 서버 디스크에 저장하고, 서버를 늘릴 때 MinIO(S3 호환)로 설정만 바꿔 확장
- **메일**: 개발 Mailpit, 운영 Gmail SMTP / **AI**: Gemini 무료 등급 → 자체 Ollama
- **구조**: 하나의 앱 안에서 기능별 모듈로 나눈 모듈러 모놀리스(`src/main/java/com/team/blog/`)

| 모듈 | 담당 |
|---|---|
| `account` | 가입·로그인·프로필·탈퇴 |
| `post` | 글쓰기·발행·공개 범위·상세·내 글 관리·읽기 권한 판정 |
| `tag` | 태그, AI 태그 추천 |
| `media` | 사진 올리기·썸네일·정리 |
| `interaction` | 댓글·좋아요·팔로우 |
| `discovery` | 홈·블로그 목록·트렌딩·검색·조회수·피드 |
| `notification` | 인앱 알림 |
| `moderation` | 신고·숨김·정지·관리자 화면 |
| `shared` | 보안·오류 응답·공통 화면·사건(event) |

## 로컬에서 실행

필요한 것: JDK 21, Docker.

```bash
docker compose up -d        # PostgreSQL·Redis·Mailpit·사진 저장소(MinIO 포크)
./gradlew bootRun           # http://localhost:8080 (개발 프로필)
```

- 가입 인증 메일은 Mailpit(http://localhost:8025)에서 봅니다.
- 기본 포트(5432·6379·1025·8025·9000)가 겹치면 `DB_PORT`·`REDIS_PORT`·`MAIL_PORT`·`MAILPIT_WEB_PORT`·`STORAGE_PORT`로 바꾸고 앱에도 같은 값을 줍니다.
- 소셜 로그인·Gmail·Gemini 키는 `.env`(git에 올리지 않음)에 넣습니다. 만드는 법은 `specs/001-auth/secrets-setup.md`.
- 관리자 화면을 보려면 DB에서 `UPDATE member SET role = 'ADMIN' WHERE handle = '내주소';` 후 머리말 [관리].

## 테스트

```bash
./gradlew test              # 통합 테스트 628개(Testcontainers로 PostgreSQL·Redis·Mailpit·MinIO를 띄움)
node src/test/js/diff.test.mjs   # 편집 비교 창 diff 자체 검사(선택)
```

## 배포

- 운영 모드로 전체를 띄우는 법·환경 변수·확장 순서: [`DEPLOY.md`](DEPLOY.md)
- 서버 한 대(Nginx + Let's Encrypt) 배포 순서: [`deploy/README.md`](deploy/README.md)
- 값 견본: `.env.example`

## 문서 위치

| 경로 | 내용 |
|---|---|
| `docs/` | 팀이 쓴 원본 설계 문서(요구사항·아키텍처·ERD·기능별 설계). 결정 근거의 원문 |
| `.specify/memory/constitution.md` | 프로젝트 헌법(원칙) |
| `specs/README.md` | 문서 간 차이와 팀 결정 기록 |
| `specs/NNN-기능/` | 기능별 `spec.md`(명세) → `plan.md`·`research.md`(설계·결정) → `tasks.md`(작업, 모두 완료) |
| `src/main/resources/db/migration/` | 실제 DB 스키마: V1 공통(테이블 20개), V2~V8 강성찬 개인 확장(테이블 8개 추가, 공통 테이블은 CHECK 교체만) |
| `db/demo/` | 앱에 바로 넣는 한국어 데모 데이터(PostgreSQL)와 데모 계정 |
| `db/mysql/` | 같은 스키마의 MySQL 사본과 데모 데이터(ERD 도구용) |
| Crowfoot ERD | https://crowfoot.java21.net/workspaces/62/models/672 (테이블 20개·관계 41개, 명세 001~024와 연결) |

## 작업 방식

기능마다 Spec Kit 순서로 진행합니다: `/speckit-specify` 명세 → `/speckit-clarify`(선택) → `/speckit-plan` 계획 → `/speckit-tasks` 작업 → `/speckit-implement` 구현.
새 기능도 같은 순서로 `specs/025-…`부터 이어 갑니다.
