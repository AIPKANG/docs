# Source Notes: 021-ai-tag-suggest

원문: `docs/34-ai-tag-suggest.md`. spec.md에서 뺀 구현 세부를 `/speckit-plan`용으로 남긴다.

## plan 단계에서 참고할 기술 결정

- 구조: `TagSuggester` 인터페이스 + `GeminiTagSuggester` / `OllamaTagSuggester` / `DisabledTagSuggester` + `TagSuggesterRouter` + 기능 스위치, 진입은 `TagSuggestService` (34 §1 A-10, §2)
- 설정값 키 `blog.ai.tag-suggest.*` (enabled, prompt-version, gemini.model/daily-limit 450/timeout 10s/max-input-chars 8000, ollama.base-url/model `qwen2.5:1.5b`/num-thread 4/timeout 30s/max-input-chars 2000/max-concurrency 1). `GEMINI_API_KEY`는 환경 변수 (34 §2)
- 공급자: Gemini API 무료 등급 → Ollama 자체 서버. HTTP 429 종류별 처리, 타임아웃·5xx는 cooldown (34 §3-1, §3-2)
- Redis 키: `ai:gemini:count:{날짜}`, `ai:gemini:exhausted`, `ai:gemini:cooldown`(60초), `ai:ollama:inflight`, `ai:tag:popular:{날짜}` (34 §3-2, §4)
- 캐시: ① `ai:tag:v{prompt-version}:{SHA-256(정리된 입력)}` 30일, ② `ai:tag:post:{postId}` 7일, 3-gram Jaccard ≥ 0.9 (SimHash는 기각) (34 §5-1, §5-3)
- 프롬프트 구성 순서(고정 지시문 → 인기 태그 → 이미 붙인 태그 → 제목·본문), 최대 출력 토큰 100 (34 §4)
- 출력 형식: JSON 스키마 `{ "tags": [string] }` maxItems 5. Ollama는 `format`에 스키마, 스키마 description이 모델에 전달되지 않으므로 지시문에 설명 포함 (34 §6, §8-3)
- 태그 정규화는 22 문서의 `TagNormalizer` 재사용 (22 §2), 금칙어는 09 §4 필터
- Ollama 실측: `num_thread`는 성능 코어 수 이하(기본값은 약 140배 느림), 2,000자 약 5.6~6.9초 (34 §8-1, §8-2)
- API: `POST /api/posts/{postId}/tag-suggestions` 요청 `{title, contentMd, currentTags, refresh}`, 응답 `{tags, provider, cached, truncated, remainingToday}`. 오류 409 `AI_CONSENT_REQUIRED`, 422 `CONTENT_TOO_SHORT`, 429 `AI_DAILY_LIMIT`, 503 `AI_UNAVAILABLE` (34 §9)
- ERD: `member.ai_consent_at timestamptz` (null = 미동의). 캐시·횟수·공급자 상태는 테이블 없이 Redis (34 ERD 변경 제안)
- 탈퇴 익명화(`MemberWithdrawalPurgeStep`, order 90)에서 `ai_consent_at` null (44 §4)

## 문서 간 차이 (spec에서 정리한 방식)

- 34 §9: 남의 글 → 403. 42 P-4·§4는 남의 것 → 404, 403은 계정 상태만 → spec은 404 기준 (EMAIL_NOT_VERIFIED만 403)
- 34 §4 지시문 "영어 소문자와 하이픈만" vs 22 T-1 허용 문자(한글·`._+#-`) → 최종 검증은 22 규칙
- 미결: 34 "후속 제안 F-1" (비공개·친구 공개 글은 Ollama로만) → spec FR-021 NEEDS CLARIFICATION
