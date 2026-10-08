# Implementation Plan: 조회수 30분 5회 — 강성찬 개인 확장

**Spec**: [spec.md](./spec.md) | **Date**: 2026-10-09

016이 기준을 설정값으로 열어 두었으므로(31 W-1) **설정값과 안내 문구만** 바꾼다. 코드 변경 없음.

- `application.yml` `blog.view`: `dedupe-window: 30m`, `max-per-window: 5`, `notice: 같은 사람은 30분에 5번까지 세요`
- `ViewProperties` 기본값(24h·1)은 공통 기준으로 그대로 둔다 → 공통으로 되돌리려면 yml 세 줄만 지우면 된다.
- 테스트: `ViewCountIT` 기대값을 새 기준으로(같은 방문자 3번 → 3, 동시 50번 → 5, 판정 기록 만료 30분, 안내 문구).
