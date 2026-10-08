# Contract: AI 태그 추천 (021)
| 경로 | 결과 |
|---|---|
| `POST /api/posts/{id}/tag-suggestions` `{title, contentMd, currentTags[], visibility, refresh}` | 200 `{tags[], provider, cached, truncated, remainingToday, message?}` · 400 `AI_PUBLIC_ONLY` · 401 · 403 · 404 · 409 `AI_CONSENT_REQUIRED` · 422 `CONTENT_TOO_SHORT` · 429 `AI_DAILY_LIMIT` · 503 `AI_UNAVAILABLE`/`AI_BUSY` |
| `GET /api/me/ai-consent` | 200 `{enabled, consented}` · 401 |
| `POST /api/me/ai-consent` | 204(동의) · 401 · 403 |
| `DELETE /api/me/ai-consent` | 204(철회) · 401 |
