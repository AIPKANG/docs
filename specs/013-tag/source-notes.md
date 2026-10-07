# Source Notes: 013-tag

원문: `docs/22-tag.md` (§1 T-1~T-10, §2 정규화, §3 입력, §4 저장, §5~§9 화면, §10 완료 기준, §11 ERD, §13 맞출 것)

## plan 단계에서 참고할 기술 결정

- `TagNormalizer` 단일 클래스: NFKC → 보이지 않는 문자 제거(12 §7-4 목록) → trim → 맨 앞 `#` 제거 → `toLowerCase(Locale.ROOT)` → 공백→`-` → `-` 정리. 패턴 `[가-힣a-z0-9._+#-]{1,30}`, `HAS_WORD [가-힣a-z0-9]`, 코드 포인트 길이 (22 §2)
- 금칙어는 09 §4-2 필터(변형 4가지) 재사용 (22 T-5)
- 오류 응답 형식 05 §4: `{ field: "tags[2]", code, value }` 배열, 400 (22 §2-2, §10)
- 검사는 발행 트랜잭션 밖(05 §7 ①), 저장은 05 §7 ⑤: `INSERT INTO tag … ON CONFLICT (name) DO NOTHING` → `post_tag` 통째로 DELETE/INSERT (22 §4)
- 태그 전용 이벤트 없음. 검색 색인은 `PostEdited`·`PostWentPublic`으로 갱신 (20 §3-1)
- 태그 목록 쿼리는 `VisibilityFilter`(06 R-2, R-2a) 조건 + `first_public_at DESC, id DESC` 커서, `ix_post_tag_tag(tag_id, post_id)`. 수천 건 넘으면 `post_tag.first_public_at` 복사 컬럼 검토 (22 §5)
- 주소 인코딩 `UriUtils.encodePathSegment`, Spring 6 suffix pattern 없음, `StrictHttpFirewall`이 `%23`·`%2B`·한글 통과하는지 테스트 (22 §5, §5-1)
- 전체 태그 목록 Redis 캐시 `tags:top` TTL 10분 (22 §6)
- API: `GET /api/tags/{이름}/posts`, `GET /api/tags?limit=100`, `GET /api/tags/suggest?q=`(로그인), `GET /api/members/{handle}/tags`, `GET /api/members/{handle}/posts?tag=` (22 §5~§8)
- 자동완성 `LIKE 'spr%'` + `ix_tag_name_prefix (name varchar_pattern_ops)`, Redis 요청 제한 1분 60번, 클라이언트 `compositionstart~end` 무시·0.3초 디바운스 (22 §7)
- ERD 변경: `post_tag.position smallint NOT NULL` + `UNIQUE(post_id, position)` + `CHECK 0~99`, `ck_tag_name` 정규식 강화, `ix_tag_name_prefix` (22 §11)
- 완료 기준 6번은 MockMvc + 실제 Security 필터, 8번은 동시 발행 10건 (22 §10)
