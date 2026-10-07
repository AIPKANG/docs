# 블로그 플랫폼 Constitution

이 헌법은 `docs/01-common-requirements.md`(공통 최소 요구사항)와 `docs/02-architecture.md`(팀 공통
아키텍처)의 확정 사항을 Spec Kit 원칙으로 옮긴 것이다. 모든 기능 명세(`specs/*/spec.md`)와 구현 계획은
이 문서를 따른다. 세부 결정의 근거는 `docs/`의 원문 문서에 남아 있다.

## Core Principles

### I. 하나의 배포 단위, 모듈러 모놀리스

- 저장소 하나, 배포 단위 하나로 만든다. MSA로 나누지 않는다.
- 기능은 모듈(account, post, tag, media, interaction, discovery, shared)로 나누고, 모듈은 다른 모듈의
  저장소·테이블을 직접 쓰지 않고 공개된 Service 또는 도메인 이벤트로만 소통한다.
- 권한 판단, 상태 전이, 검증 같은 업무 규칙은 Service 계층에 둔다. 표현 계층(SSR 또는 REST)과 인증 방식은
  팀원마다 고를 수 있지만 업무 규칙과 ERD는 공통이다.

근거: 팀원 세 명이 각자 개인 확장을 붙여도 공통 모듈이 깨지지 않아야 한다.

### II. 공통은 바꾸지 않고, 확장은 추가만 한다 (NON-NEGOTIABLE)

- 공통 ERD는 수정하지 않는다. 개인 확장은 새 테이블·nullable 컬럼·새 모듈·새 규칙 Bean으로 추가만 한다.
- 개인 확장을 더해도 공통 완료 기준(Tier A·B)은 그대로 만족해야 한다.
- 정책 수치(태그 최대 개수, 조회수 중복 기준, 페이지 크기 등)는 코드가 아닌 설정값으로 둔다.
- DB 스키마는 Flyway 마이그레이션으로만 바꾼다. 정하지 않은 FK 삭제 동작은 `ON DELETE RESTRICT`로 적는다.

### III. 서버가 권한을 지킨다 (NON-NEGOTIABLE)

- 권한은 두 겹으로 검사한다: 화면에서 숨기고, 서버(Service)에서 다시 검사한다. 화면에서 숨기는 것만으로
  막지 않는다.
- 현재 사용자는 인증 정보에서만 꺼낸다. 작성자 ID를 요청 값으로 받지 않는다.
- 남의 비공개·임시·삭제된 리소스와 존재하지 않는 리소스는 똑같이 404로 응답한다. 응답 본문과 링크 미리보기도
  구별할 수 없어야 한다.
- 글 읽기 판정은 하나의 접근 정책에 모으고, 목록 조회도 같은 규칙의 조건만 쓴다. 공개 범위 확장은 규칙을
  추가하는 방식으로 한다.

### IV. 사용자 입력은 안전하게 보여준다

- 본문은 Markdown 원문을 저장하고, HTML은 서버가 CommonMark+GFM으로 렌더링한 뒤 허용 목록 방식으로 정화해서
  저장·출력한다. 사용자가 직접 쓴 HTML은 글자로 보인다.
- 제목·소개·닉네임·댓글은 글자로만 다룬다(이스케이프). 보이지 않는 문자·방향 뒤집기 문자·제어 문자는 제거한다.
- 링크는 http·https·mailto·상대 경로만 허용하고, 외부 링크는 새 탭과 `noopener noreferrer nofollow ugc`로 연다.
  이미지는 우리 저장소 것만 보여주고 외부 이미지는 링크로 바꾼다.
- CSP, `nosniff`, `Referrer-Policy`를 모든 응답에 건다. 비밀값은 환경 변수로만 주입한다.

### V. 부가 기능은 핵심을 막지 않는다

- 조회수 집계, 알림, AI 추천, 통계가 실패해도 글쓰기와 읽기는 성공한다.
- 부가 기능은 도메인 이벤트를 커밋 후에 구독해서 붙이고, 기존 모듈 코드를 고치지 않는다.
- 트랜잭션 안에서 외부 호출(LLM, 파일 저장, HTTP)을 하지 않는다.
- 같은 요청을 여러 번 보내도 결과는 한 번만 생긴다(발행 연타, 좋아요 동시 요청 등).

### VI. 실제 환경으로 검증한다

- 권한·공개 범위·소유 검사에 관련된 기능은 통합 테스트가 필수다.
- 테스트는 H2가 아닌 실제 PostgreSQL(Testcontainers)로 돌린다.
- 각 기능 명세의 수용 시나리오는 관찰 가능한 결과로 적고, 그대로 테스트로 옮길 수 있어야 한다.

## 기술 제약

| 영역 | 확정 사항 |
|---|---|
| 언어·프레임워크 | Java 21, Spring Boot (버전은 팀 확정) |
| 데이터 | PostgreSQL(`pg_trgm`), Spring Data JPA, Flyway |
| 캐시·버퍼·세션 | Redis (AOF `everysec`, `noeviction`), Spring Session 14일 |
| 파일 저장소 | MinIO(S3 API) + Presigned URL(SigV4). 운영은 NHN 제공 MinIO, 로컬은 커뮤니티 포크 이미지 |
| 인증 | 이메일 가입(인증 필수) + Google + GitHub. 로그인 수단이 다르면 별도 계정 |
| Markdown·정화 | commonmark-java 0.30.0 + GFM, OWASP Java HTML Sanitizer 20260924.2 |
| 테스트 | JUnit 5, Testcontainers, Spring Security Test |
| 실행 | Docker Compose, 비밀값은 환경 변수 |

비기능 최소선: 목록·상세 서버 응답 300ms 이내(글 1만 건), 목록 쿼리 수가 글 수에 비례하지 않음(N+1 금지),
375px부터 데스크톱까지 가로 스크롤 없음, 공개 글만 sitemap·OG에 노출.

## 개발 흐름

1. 기능은 Spec Kit 순서로 진행한다: `/speckit-specify` → (`/speckit-clarify`) → `/speckit-plan` →
   `/speckit-tasks` → (`/speckit-analyze`) → `/speckit-implement`.
2. 명세(`spec.md`)에는 무엇과 왜만 적고 기술 선택은 적지 않는다. 기술 선택은 `plan.md`에서 이 헌법의 기술
   제약을 따른다.
3. 기능 우선순위는 Tier A(공통 필수) → Tier B(공통 권장) → Tier C(2차 후보) → 개인 확장 순이다.
4. 명세가 `docs/`의 원문과 다르면 회의 결정 기록(`docs/01-common-requirements.md` §4)이 우선하고, 명세를 고친다.

## Governance

- 이 헌법은 다른 모든 개발 관행보다 우선한다. 모든 PR 리뷰는 원칙 I~VI 준수 여부를 확인한다.
- 개정은 팀 회의에서 합의하고, 결정 기록에 날짜와 함께 남긴 뒤 이 문서의 버전을 올린다.
- 버전 규칙: 원칙 삭제·재정의는 MAJOR, 원칙·절 추가는 MINOR, 문구 정리는 PATCH.
- 원칙을 어기는 복잡도는 `plan.md`의 Complexity Tracking에 이유를 적어야 한다.

**Version**: 1.0.0 | **Ratified**: 2026-10-02 | **Last Amended**: 2026-10-07
