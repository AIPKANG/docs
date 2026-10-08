# Research: 댓글·답글 (014-comment)

**Phase 0** · 2026-10-08 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/21-comment.md`, 005·006·010·011·012 구현

사용자 지시: 질문 없이 기본값. V1 `comment` 테이블(복합 FK `(post_id, parent_id)`, `reply_to_member_id`, 숨김 칸)이 이미 21 §15를 반영하고 있어 마이그레이션이 없다.

## R-1. 모듈
- **Decision**: 새 `interaction` 모듈(02 §3: 댓글·좋아요)에 `CommentService`·`CommentQuery`. 글을 읽을 수 있는지와 댓글 수 증감은 post 모듈 공개 Service(`PostReadAccess`, `PostCounters`)로만 한다(헌법 I). 글 상세 화면(post.web)은 `CommentQuery`의 첫 페이지를 받아 그린다.

## R-2. 작성 (FR-001~FR-009, FR-030, FR-031)
- **Decision**: 검사 순서 FR-003 그대로: 로그인 → 인증(`requireWritable`) → 1분 10개(`RedisRateLimiter`) → 내용 정리·길이(`CommentContentRules`: NFC → 숨은·방향·제어 문자 제거(줄바꿈 유지) → `\r\n`→`\n` → strip → 빈 줄 연속 하나로, 1~1000 코드 포인트) → 글 읽기 가능(`PostReadAccess.requireCommentable`: 발행·휴지통 아님·006 판정·숨김 아님 — 아니면 404) → 답글 대상(같은 글·정상 상태, 아니면 400 `REPLY_TARGET_UNAVAILABLE`) → 10초 중복(Redis `cmt:dedupe:{member}:{post}:{sha256(내용|대상)}` `SET NX EX 10` → 이미 있으면 처음 댓글을 그대로 201).
- 트랜잭션: 대상 최상위를 `FOR SHARE`로 잠가 동시 삭제와 직렬화(FR-028) → `parent_id = 최상위`, `reply_to_member_id`는 대상이 답글이고 그 작성자가 내가 아닐 때만(FR-005) → INSERT → `PostCounters.adjustComments(+1)` → 사건 `CommentCreated`(커밋 후 구독, 017).

## R-3. 조회 (FR-011~FR-021)
- **Decision**: 최상위 21개(오래된 순, 커서 `(created_at, id)`) + 그 최상위들의 처음 답글 3개와 답글 수(`row_number`·`count OVER`) — 페이지당 SQL 2번. 답글 더 보기 `GET /api/comments/{rootId}/replies?cursor=`(20개). 상태 판정 순서: 작성자 탈퇴(유예 포함) → 삭제된 자리 → 숨김 → 정상(FR-013). 탈퇴·삭제·남이 보는 숨김은 `content`·`author`를 null로 보낸다(FR-014). "@닉네임에게"는 대상 회원의 지금 닉네임(탈퇴면 "탈퇴한 사용자에게"). [작성자] 배지, 버튼 표시 플래그(`canReply`, `canEdit`, `canDelete`, `canReport`). `?comment={id}`(상세)·`?around={id}`(API): 대상의 최상위부터 20개, 대상이 4번째 이후 답글이면 대상까지 펼침, 앞이 있으면 `prevCursor`. 대상이 없거나 다른 글·삭제·숨김이면 첫 페이지.
- 상세 SSR에 첫 20개 포함, `?commentCursor=` 링크로 스크립트 없이 [댓글 더 보기].

## R-4. 수정·삭제 (FR-022~FR-029)
- **Decision**: 수정: 1분 20번, 본인만(남의 것·삭제된 자리 404), 숨김 409 `COMMENT_HIDDEN`, 지금 글을 읽을 수 있어야 함, 같은 내용이면 그대로, `updated_at`만(이력·알림 없음). 삭제: `requireLoggedIn`(인증 전도 자기 것 가능), 최상위 `FOR UPDATE` → 본인만(404) → 최상위에 답글이 있으면 `content=''`·`deleted_at` 자리, 아니면 행 삭제, 답글을 지워 자리만 남은 최상위에 답글이 없으면 자리도 삭제 → 댓글 수 −1(이미 숨김이었으면 변화 없음) → 사건 `CommentDeleted`(017이 알림 삭제). 글 상태(휴지통·작성자 탈퇴 유예)로 볼 수 없으면 404.

## R-5. 숨김
- 관리자 숨김·해제는 022. 표시 규칙과 댓글 수 규칙(숨김 −1)은 여기 맞춰 둔다.

## R-6. 테스트
- 작성(권한·순서·1단계·대상 기록·중복·제한·길이·정리), 조회(정렬·커서·답글 3+더 보기·상태 표시·내용 비노출·around), 수정·삭제(권한·자리·자리 정리·댓글 수), 동시 삭제와 답글, 글 상태(비공개·휴지통·탈퇴), 012 매트릭스 표 3 행, 화면(SSR·버튼·이스케이프·pre-line).

## 남은 확인 사항
| # | 내용 | 기본값 |
|---|---|---|
| U-1 | 신고 버튼 동작 | 022 |
| U-2 | 알림 | 017이 `CommentCreated`·`CommentDeleted` 구독 |

## 구현 메모 (/speckit-implement, 2026-10-08)
- **I-1.** 글 상세는 post 모듈의 확장점 `PostDetailSection`으로 댓글 영역을 받는다(post가 interaction을 직접 부르지 않음, 헌법 I). 상세는 이미 읽기 판정을 했으므로 `pageOfReadable`로 다시 판정하지 않는다(010 쿼리 수 유지).
- **I-2.** 10초 중복 방지는 `SET NX "0"`으로 자리를 먼저 잡고 만든 뒤 번호로 바꾼다(동시에 두 번 눌러도 하나). 요청 횟수 검사가 먼저라 중복 요청도 1분 10개에 센다(FR-003 순서).
- **I-3.** 수정 시각은 `GREATEST(앱 시각, created_at + 1µs)` — 앱과 DB 시계가 어긋나도 `ck_comment_edited`를 지키고 "수정됨"이 붙는다(매트릭스 테스트에서 발견).
- **I-4.** 012 `PermissionMatrixIT`에 표 3(댓글 보기·쓰기·수정·삭제) 행을 더했다. 전체 테스트 545+개 통과.
