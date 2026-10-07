# 통합 ERD 군더더기 점검

> 작성일 2026-10-07 · 상태: **제안 (팀 결정 대기)**
> 대상: [V1](../erd/V1__common_schema.sql) · [51 통합 명세](./51-erd-unified.md) — 20개 테이블, 149개 컬럼, FK 41개
> 기준: 컬럼마다 "누가 읽는가, 다른 곳에서 구할 수 있는가, 어긋나면 누가 맞추는가"를 01~45 문서와 대조했다. **쓰기만 하고 읽는 곳이 없거나, 다른 값에서 바로 구할 수 있는데 따로 맞춰야 하는 것**을 군더더기로 본다.
> 2026-10-07 재확인: 처음 판단 중 근거가 틀린 것을 고치고 등급을 다시 매겼다 (§5).
> V1·51·ERD Cloud는 아직 고치지 않았다. 아래 결정이 나면 함께 고친다.

---

## 1. 요약

| 구분 | 항목 |
|---|---|
| **A. 뺀다** (근거 확실) | `friendship` 테이블, `image.original_name`, `member.status`의 `SUSPENDED`, `report_case.handled_at`/`closed_at` 중 하나 |
| **B. 고친다** (구조 문제) | 글·댓글의 `hidden_by`·`hidden_reason` |
| **C. 팀이 정한다** (취향·비용) | `member.profile_image_url`, `notification.actor_count`, `image.width`·`height` |
| **D. 남긴다** | §5 |
| **문서 불일치** | §6 — ERD와 무관하게 고쳐야 하는 문서 |

---

## 2. A. 뺀다

### A-1. `friendship` 테이블

`post.visibility`·`notification.type` CHECK에 친구 관련 값이 없어서 **이 테이블을 읽는 공통 기능이 없다** (V1 머리말: "활성화하지 않는다"). 01은 "구현은 선택". 그런데 탈퇴 처리(13 §3-3 6단계, 44 order 60)는 신경 써야 한다.
→ V1에서 빼고, 06 §6의 DDL을 친구 공개 구현자가 붙이는 확장 마이그레이션으로 둔다.

### A-2. `image.original_name`

`NOT NULL`인데 저장 키에도 안 쓰고(04 §4-2) **화면 어디에도 보여주지 않는다**(23 §4). 읽는 곳이 없고, 파일 이름에 개인정보가 들어가기 쉽다.
→ 컬럼과 업로드 API의 `originalName` 입력을 뺀다.

### A-3. `member.status`의 `SUSPENDED`

| 항목 | 내용 |
|---|---|
| 지금 (51) | `status = SUSPENDED`는 "정지 중, 근거는 `member_suspension`". 기간이 지난 뒤 로그인하면 `lifted_at`을 기록하고 `ACTIVE`로 돌린다 |
| 문제 1 | 같은 사실을 두 곳에 둔다. 기간이 끝난 회원이 로그인하지 않으면 `status`는 계속 `SUSPENDED`로 남는다 → 관리자 회원 화면이 틀린 상태를 보여준다 |
| 문제 2 | 탈퇴와 정지가 한 컬럼을 다툰다. 탈퇴 유예 중(`WITHDRAWN`)인 회원을 정지하면 `SUSPENDED`로 바꿔야 하는데, `ck_member_withdrawn`(`status = WITHDRAWN` ⇔ `withdrawn_at` 있음) 때문에 막힌다 |
| 바꾸면 | 정지 여부 = `member_suspension`에 `lifted_at IS NULL AND (ends_at IS NULL OR ends_at > now())`인 행이 있는가. 로그인 때 한 번 읽는다 (정지하면 세션을 지우므로 요청마다 볼 필요 없다, 42 P-7). 사유·종료일 안내도 어차피 이 행에서 읽는다. `status`는 `ACTIVE`·`WITHDRAWN`만 |

### A-4. `report_case.handled_at` / `closed_at` 중 하나

관리자가 처리하면 두 값이 같은 순간에 들어간다. 왜 닫혔는지는 `status`가, 누가 닫았는지는 `handled_by`가 말해 준다.
→ 하나만 남긴다. `handled_at`을 남기면 대상이 사라져 자동으로 닫힐 때 `handled_by = null`, `handled_at = 닫힌 시각`. 어느 쪽을 남길지는 회의 H5 결정을 따른다.

---

## 3. B. 고친다 — 숨김 기록 (`post`·`comment`의 `hidden_by`, `hidden_reason`)

| 항목 | 내용 |
|---|---|
| 지금 | 숨김은 **신고 사건 처리로만** 생긴다 (43 §3, 처리 화면에서 "숨기기 사유 [스팸·광고 ▾]"). 해제도 처리됨 탭에서만 한다 (43 §4-3) |
| 문제 1 | `hidden_by`는 그 사건의 `report_case.handled_by`와 같은 값이다 |
| 문제 2 | 관리자가 고른 **숨김 사유가 `report_case`에는 없고 `hidden_reason`에만 있다.** 해제하면 "`hidden_at` 등을 비운다"(43 §4-3)이므로 사유와 처리자가 지워져 **기록이 사라진다.** 43 §3 "누가·언제·무엇을 했는지 남는다"와 어긋난다 |
| 바꾸면 | `report_case`에 `action_reason varchar(30)`을 둔다 (반려면 null). `post`·`comment`에는 목록 거르기용 `hidden_at`과, 어느 사건으로 숨겼는지 `hidden_case_id`(FK → `report_case`)만 둔다. 해제해도 사건 행은 남는다 |
| 결과 | 컬럼 4개 → 2개, `member` FK 2개 → `report_case` FK 2개 |

---

## 4. C. 팀이 정한다

| # | 항목 | 문제 | 그대로 둘 이유 | 의견 |
|---|---|---|---|---|
| C-1 | `member.profile_image_url` | `profile_image_id`로 `image` PK를 한 번 찾으면 되는 값을 따로 저장한다. 바꾸는 곳이 사진 변경·기본 이미지·탈퇴 세 군데이고, 탈퇴에서는 id(order 40, 강성찬)와 url(order 90, 공통)을 **다른 담당자가** 비운다 | 11 §4-4가 "목록 카드에서 `image` JOIN을 줄이려고 그대로 둔다"고 이미 정했다. 모두 한 트랜잭션 안이라 실제로 어긋날 일은 적다 | 빼는 쪽 권장. 다만 이미 내린 결정이라 회의 안건으로 |
| C-2 | `notification.actor_count` | `notification_actor` 행 수와 같다. 좋아요·취소·탈퇴 세 곳에서 다시 맞춘다 (25 §4·§8) | 세 곳 모두 강성찬 담당의 같은 코드라 관리가 쉽다. "0명이면 삭제" 판단이 싸다 | 어느 쪽이든 무방 |
| C-3 | `image.width`, `height` | "해상도 검사 기록"(04 §5)으로 저장만 한다. 정화 규칙(12 §4)이 `img`의 `width`·`height` 속성을 허용하지 않아 **화면에 쓸 길도 없다** | 렌더러가 `<img width height>`를 넣어 사진 로딩 중 화면 밀림을 막는 데 쓸 수 있다 | 그 용도로 쓸지 정하고, 안 쓰면 뺀다 |

---

## 5. D. 남긴다

| 컬럼 | 다른 데서 구할 수 있는 것 | 남기는 이유 |
|---|---|---|
| `post.thumbnail_url` | 첫 사진의 `thumb_storage_key` | (처음엔 FK로 바꾸자고 했으나 철회) 썸네일이 없는 옛 사진은 원본 주소로 대체하는 규칙이 있고(10 §6), 저장소 주소가 박히는 문제는 `content_md` 본문도 똑같아서(12 S-6) 이 컬럼만 바꿔서 얻는 게 없다 |
| `post.content_html`, `excerpt`, `render_version` | `content_md` | 렌더링·정화(12)는 비싸고 발행할 때 한 번 만든다 |
| `post.view_count` | `post_view_daily` 합계 | 일별은 90일 뒤 지워진다 (31) |
| `post_view_daily` | — | 공통 기능 중 읽는 곳은 없지만 31 W-7·32 T-10에서 "작성자 통계·점수식 B 대비로 공통에 유지"로 이미 정했다. 다만 01 §2-4는 아직 김민서 개인 확장 목록에 넣어 두었다 (§6) |
| `post.like_count`, `comment_count` | 행 수 | 모든 카드에 나온다 |
| `post.published_at`, `first_public_at`, `edited_at`, `updated_at` | 서로 | 뜻이 다르다 (05 §2) |
| `post.edit_version`, `post_draft.edit_version` | 서로 | 발행본과 작업본의 버전이 따로 움직인다 (04, 05) |
| `comment.reply_to_member_id` | 대상 답글의 작성자 | 대상 답글이 지워져도 "누구에게"가 남아야 한다 (21 CM-2) |
| `report_case.target_type`, `target_author_id`, `snapshot_*` | `post_id`·`comment_id` | 대상이 지워지면 FK가 `SET NULL`이다. 관리자도 비공개 글을 못 보므로 스냅샷이 필요하다 (43 H-5) |
| `notification.last_actor_id` | `notification_actor` | 묶지 않는 알림(댓글·새 글)은 `notification_actor` 행이 없다 |
| `notification.result` | `report_case.status` | 받은 알림은 나중에 사건이 바뀌어도 바뀌지 않아야 한다 |
| `image.status`, `detached_at` | 연결 여부 | 정리 배치가 인덱스로 바로 찾는다 (04 §4-4) |
| `image.thumb_size_bytes` | — | 용량 계산 (23 §3). 썸네일이 있는지도 이 값으로 알 수 있다 |

---

## 6. 문서 불일치 (ERD 결정과 무관하게 고칠 것)

V1이 `member`의 정지·동의 컬럼을 `member_suspension`·`member_agreement`로 옮겼는데, 옛 컬럼 이름이 문서에 남아 있다.

| 문서 | 남아 있는 것 | 지금 V1 |
|---|---|---|
| 43 §6 | 자동 해제가 `suspended_until`을 읽음 | `member_suspension.ends_at`·`lifted_at` |
| 43 "ERD 변경 제안" SQL | `ALTER TABLE member ADD suspended_until, suspended_reason`, `report.target_type/target_id` | `member_suspension`, `report_case` |
| 44 §4 order 90 | `ai_consent_at`·`suspended_until`·`suspended_reason`을 null로 | 이 컬럼들은 `member`에 없다. 동의·정지 이력을 탈퇴 때 어떻게 할지는 미정 (ERDCLOUD-CHECKLIST) |
| 01 §2-4 | `post_view_daily`가 김민서 개인 확장 목록에 있음 | 31·32에서 공통으로 결정 |

---

## 7. 처음 판단에서 고친 것

| 항목 | 처음 | 다시 보니 |
|---|---|---|
| `profile_image_url` | "탈퇴 4·7단계 사이에 값이 어긋난다", "저장소 호스트가 박힌다" → 확실히 뺀다 | 탈퇴는 회원 1명 = 트랜잭션 1개라 밖에서 어긋난 값을 볼 수 없다. 호스트 문제는 본문도 같다. 11 §4-4가 이미 "그대로 둔다"고 정했다 → **C로 내림** |
| `SUSPENDED` | "43이 읽는 `suspended_until`이 없다" | 51이 `lifted_at`으로 자동 해제를 다시 정의해 두었다. 대신 상태가 남는 문제와 탈퇴 CHECK 충돌을 확인했다 → **A 유지, 근거 교체** |
| `actor_count` | 확실히 뺀다 | 관리 지점이 한 사람의 같은 코드라 비용이 작다 → **C로 내림** |
| `thumbnail_url` | FK로 바꾸자 | 원본 대체 규칙과 본문 주소 문제 때문에 이득이 없다 → **D로 철회** |
| `post_view_daily` | 개인 확장으로 옮기자 | 31·32에서 공통 유지로 이미 결정 → **D**, 01 문서만 고친다 |
| `hidden_by`·`hidden_reason` | 사건 FK로 줄이자 (선택) | 해제하면 숨김 사유·처리자 기록이 사라지는 문제를 확인했다 → **B로 올림** |
| `image.width`·`height` | 렌더러가 쓰면 남기자 | 정화 규칙이 그 속성을 허용하지 않아 지금은 쓸 길이 없음을 확인 → **C 유지** |

---

## 8. 결정 체크

- [ ] A-1 `friendship`을 확장 마이그레이션으로 이동
- [ ] A-2 `image.original_name` 삭제
- [ ] A-3 `SUSPENDED` 삭제, 정지 여부는 `member_suspension`으로 판단
- [ ] A-4 `handled_at`·`closed_at` 중 하나 삭제 (회의 H5)
- [ ] B `hidden_by`·`hidden_reason` → `hidden_case_id` + `report_case.action_reason`
- [ ] C-1 `member.profile_image_url`
- [ ] C-2 `notification.actor_count`
- [ ] C-3 `image.width`·`height`
- [ ] §6 문서 불일치 4건 정리

결정이 나면 V1 → 51 → ERD Cloud → 관련 문서 순서로 반영하고, `scripts/check-ddl.sh`로 V1을 다시 확인한다.
