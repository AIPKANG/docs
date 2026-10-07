# Source notes: 020-search

원문: `docs/33-search.md` (참고: `docs/22-tag.md` §2 `TagNormalizer`, `docs/31-view-count.md` §2-1 방문자 키, `docs/06-visibility.md` §7)

## plan 단계에서 참고할 기술 결정

- PostgreSQL `pg_trgm` + GIN 인덱스 4개: `ix_post_title_trgm`, `ix_post_content_trgm`(`content_md`), `ix_member_nickname_trgm`, `ix_member_handle_trgm` (33 §6). trigram이라 2글자는 인덱스 불가 → Q-2. `CREATE EXTENSION`은 DB 관리자 권한 필요 여부 확인 (33 ERD 변경 제안). 03에 E-25 추가안
- 비교는 `ILIKE`, `%`·`_`·`\` 이스케이프 (33 §2)
- 실행: 단계마다 ① 최근 글 `blog.search.recent-window=3000` 안에서 10개 → ② 모자라면 가장 긴 단어로 제목·태그·본문 후보를 따로 조회해 `UNION` 후 전체 조건 확인 (33 §4, Q-5). `OR` + 태그 `EXISTS`로는 본문 인덱스를 못 써 1,129ms였던 기록 (33 §8-2 주석)
- 공개 범위는 `VisibilityFilter`(06 §7) 재사용, 블로그 안 검색은 `author_id = :blogOwner` 추가 (33 §4)
- 커서 `{단계}:{first_public_at}:{id}`, 최신순은 단계 0 (33 §3-3)
- 화면/API: `GET /search?q&tab=posts&sort=relevance`(SSR), `GET /api/search/posts?q&sort=relevance|latest&cursor`, `GET /api/search/people?q`, `GET /@{handle}?q` (33 §5). 응답 JSON에 `snippetHtml`, `notice: "TWO_CHAR_TITLE_TAG_ONLY"` (33 §5)
- 강조는 `<mark>`, 이스케이프 후 적용 (33 Q-6, §5)
- 요청 제한 1분 30번, 키는 31 §2-1 방문자 키 (33 §5). `<meta name="robots" content="noindex">` (33 §5)
- 본문 TOAST 압축 유지 (압축 끄면 오히려 느림) (33 §8-1)
- 측정 기록: 글 10만 개, 최악 101ms (33 §8) — 성능 테스트 기준으로 재사용
- 태그 비교: 33 §2는 "소문자로 바꿔 부분 일치", 22 §2는 검색도 `TagNormalizer`를 쓴다고 적음 → 같은 정규화 클래스 사용 권장
- 미결: `#spring` 입력 처리 (33 "다른 담당자와 맞출 것", 태그 담당 결정) → spec FR-020
