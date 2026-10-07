# Quickstart: 글 발행·수정 (005)

1. `./gradlew test --tests '*Publish*' --tests '*TagNormalizer*' --tests '*PostDetail*'`.
2. 로컬: 새 글 → 제목·본문 → [발행] → 공개 범위·태그 → [발행] → `/@주소/posts/{id}`로 이동, 로그아웃 창에서도 보임.
3. 다시 열어 고치고 자동 저장 → 로그아웃 창은 이전 내용 → 다시 발행 → "수정됨 · 날짜", 주소 같음.
4. [발행] 연타 → 요청은 하나만 처리(네트워크 탭 409 `IN_PROGRESS` 뒤 재시도 200).
