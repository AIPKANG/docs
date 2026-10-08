# Implementation Plan: AI 태그 추천

**Branch**: `021-ai-tag-suggest` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
발행 설정의 [✨ AI 태그 추천]·[다시 추천]을 누를 때만, 동의한 회원의 자기 공개 글에 대해 정리된 제목·본문 앞부분으로 태그를 최대 5개(남은 자리만큼) 추천한다. 공급자는 Gemini 무료 등급 → 하루 한도·429·일시 중단이면 Ollama(자체 서버)로 자동 전환. 결과는 Redis에 두 단계(같은 내용 30일·같은 글 비슷한 내용 7일)로 저장해 재사용하고, 재사용은 개인 하루 20회에서 빼지 않는다. 결과는 013 태그 규칙·금칙어로 거르고 누른 것만 칩으로 더한다. 어떤 실패도 글쓰기·자동 저장·발행에 영향이 없다.

## Constitution Check
I(tag 모듈 안 `suggest` 패키지, account의 동의 Service·post의 설정·인기 태그만 씀) · II(새 컬럼 대신 V1 `member_agreement`의 `AI` 종류, 상태·캐시·횟수는 Redis, 모든 기준값 `blog.ai.tag-suggest.*`, 키는 환경 변수) · III(자기 글만 404, 인증 전 403, 공개 글만) · IV(후보는 `textContent`) · V(공급자 실패는 503 안내만, 시간 제한 10초/30초) · VI(가짜 HTTP 공급자로 전환·캐시·한도 통합 테스트) — 위반 없음.

## Project Structure
```text
tag/application/suggest/{AiTagProperties, AiInputCleaner, TrigramSimilarity, TagSuggester, AiProviderState,
    AiSuggestionCache, TagSuggestionService}
tag/infra/{GeminiTagSuggester, OllamaTagSuggester}, tag/web/TagSuggestionController
account/application/AiConsentService, shared/error/AiSuggestException
templates/post/editor.html(추천 칸·동의 창), settings/settings.html(동의 철회), static/js/editor/{ai-tags.js, publish.js(칩 연결)},
static/js/account/ai-consent.js
tests: tag/integration/AiTagSuggestIT(가짜 Gemini·Ollama 서버), tag/unit/AiInputCleanerTest
```
