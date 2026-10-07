# Research: 이미지 업로드 (008-image-upload)

**Phase 0** · 2026-10-07 · 입력: [spec.md](./spec.md), [source-notes.md](./source-notes.md), `docs/04-draft-and-image.md` §4·§6, `docs/23-image.md`, `docs/10-post-list.md` §6, 003(media 모듈)·004·005·007 구현

사용자 지시: 질문 없이 기본값. 003이 만든 `media` 모듈(`ImageStorage`, S3 구현, presign/complete, 정리 작업)을 **넓힌다**.

## R-1. 승인(presign) — 글 사진 (FR-003, FR-008, FR-023~FR-025)
- **Decision**: `POST /api/images/presign {purpose:"POST", contentType, size, thumbSize?, originalName}`. 검사 순서: 권한(401/403) → 형식 4종(400 `IMAGE_INVALID`/`TYPE`) → 원본 1..10MB, 썸네일 1..1MB(없으면 썸네일 없이) → 1분 20장(429 `RATE_LIMITED`) → 하루 200장(Redis `img:daily:{memberId}:{yyyyMMdd KST}` INCR, TTL 2일, 실패한 업로드도 셈, 429 `DAILY_UPLOAD_LIMIT`) → 트랜잭션: `pg_advisory_xact_lock(memberId)`(media가 회원 테이블을 잠그지 않도록 회원별 잠금) → 사용량 `sum(size_bytes + coalesce(thumb_size_bytes,0))` + 이번 크기 > 1GB면 409 `STORAGE_QUOTA_EXCEEDED` → TEMP 행(신고 크기 기록) → 원본·썸네일 5분 PUT 주소.
- 응답 `{imageId, uploadUrl, method, headers, expiresAt, thumbUploadUrl?, thumbHeaders?}`(003 응답에 칸 추가). 서명 헤더에 `Cache-Control: public, max-age=31536000, immutable`(FR-007)을 넣어 저장소가 그대로 돌려주게 한다.
- 썸네일 키 `{원본 키에서 확장자 앞}_thumb.webp`, 썸네일은 WebP만(브라우저가 WebP를 못 만들면 썸네일 없이 올리고 카드·GIF는 원본을 쓴다 — 10 §6 "썸네일 없는 옛 사진은 원본").

## R-2. 완료(complete) 검사 (FR-005, FR-031)
- **Decision**: 원본: 존재·실제 크기 ≤ 신고·≤10MB, 앞부분 형식 = 신고 형식, 가로·세로 ≤ 10000(GIF는 ≤1920), JPEG 메타데이터(EXIF) 있으면 거부(위치 정보 보호, 브라우저가 지워야 함), GIF는 파일 전체를 받아 블록만 훑어 프레임 수 ≤300(`GifFrameCounter`, 디코딩 없음). 썸네일: 존재·≤1MB·≤신고·WebP·가로 ≤640. 실패하면 두 파일과 행을 지우고 400 `IMAGE_INVALID`+`detail`. 통과하면 실제 크기·해상도 기록(상태 TEMP 유지), 응답 `{imageId, url, thumbUrl, width, height}`.
- `ImageStorage.read(key, maxBytes)` 추가(GIF 전체 확인용).

## R-3. 글 연결 (FR-019, FR-020, FR-010)
- **Decision**: media의 공개 Service `PostImageService.sync(postId, authorId, urls, now)`가 `post_image`(글-사진 연결, media 소유)를 고친다: 주소 → 저장 키(우리 저장소 접두어 뒤) → 그 회원이 올린 글 용도(`POST`) 사진만 → 연결 집합 교체, 새로 연결된 사진 `ATTACHED`·`detached_at NULL`, 이 글에서 빠지고 다른 글에도 없는 사진 `detached_at = now`.
  - 호출 시점: 임시글 수동 저장·영구 반영(본문 주소), 발행 글 작업본 저장·반영(발행본 + 작업본 주소 합집합 — 독자가 보는 사진을 끊지 않음), 발행(새 본문), 변경 취소(발행본). 주소 추출은 007 렌더러 결과의 `imageUrls`.
  - 발행 때 남의 사진·없는 사진 주소(우리 저장소 주소인데 내 글 사진이 아님)는 400 `VALIDATION_FAILED` + `contentMd: INVALID_IMAGE`(FR-010). 저장·반영 때는 연결만 하지 않는다(글 손실 방지).
- 카드 썸네일(FR-020): 발행 때 첫 사진의 썸네일 주소(없으면 원본).

## R-4. 정리 (FR-021, FR-022, FR-026)
- **Decision**: 003 정리 작업 그대로(TEMP 24h, 끊긴 지 7일, 파일 → 행 순서 재시도). 조건부 DELETE에 `NOT EXISTS post_image` 추가. 사용량은 행 합계라 행이 지워질 때 돌아온다.

## R-5. GIF 표시 (FR-032, 23 §5-2)
- **Decision**: 007 `MarkdownTransformer`에 규칙 추가: 우리 저장소 `.gif` 이미지 → `<a href=원본 gif title="움직이는 이미지 재생" target=_blank rel=…><img src=썸네일 alt …></a>`. 썸네일 주소는 `ImageThumbnails`(media 공개 Service: 저장 키 → 썸네일 키) 조회, 없으면 원본 GIF를 그대로 `<img>`. 허용 목록·CSP 변경 없음. `/js/gif-play.js`가 `a[href$=".gif"] > img`를 찾아 클릭·Enter로 원본 ↔ 정지 전환, CSS `a[href$=".gif"]::after { content: "▶ GIF" }`. 렌더링 규칙이 바뀌므로 `RENDER_VERSION` 2.

## R-6. 대체 저장소 (FR-015)
- **Decision**: `blog.storage.type: s3 | local`. `LocalImageStorage`: 파일을 `blog.storage.local-dir`에 저장, 업로드 주소는 우리 서버 `PUT /api/images/local-upload?key=…&exp=…&sig=…`(HMAC-SHA256, 5분, Content-Type 서명 포함), 공개 주소 `/media/{key}`(정적 핸들러, 1년 immutable 캐시). 개발·저장소 없는 환경 전용.

## R-7. 사용량 화면 (FR-027)
- **Decision**: `GET /api/me/storage` → `{usedBytes, quotaBytes, todayCount, dailyLimit}`. 설정 화면에 "저장 공간 0.3GB / 1GB · 오늘 12 / 200장". 에디터 문구: 409 "저장 공간(1GB)을 다 썼어요. 쓰지 않는 사진이 든 글을 지우면 다음 날 정리돼요", 429 하루 한도 "오늘은 사진을 200장까지 올릴 수 있어요", 90% 넘으면 "저장 공간의 90%를 썼어요".

## R-8. 에디터 (FR-001, FR-002, FR-017, FR-018, FR-028~FR-030, FR-033)
- **Decision**: `static/js/editor/image-upload.js`: 붙여넣기·끌어놓기·[사진] 버튼 → 캔버스로 원본(긴 변 1920, WebP 0.8, EXIF 자동 제거; GIF는 그대로)과 썸네일(가로 640, WebP 0.8, GIF는 첫 장면) → presign → 두 PUT → complete → 커서 자리에 `![](주소)`(대체글 비움, 파일 이름 넣지 않음). 실패·오프라인: 원본 Blob을 IndexedDB(`pendingImages`)에 두고 `![업로드 대기](local:{id})`, `online`이면 재시도해 바꿔 넣는다. 움직이는 WebP·APNG 안내 문구. 발행 설정에 "대체글이 없는 사진 n장" + 사진별 입력칸(입력하면 본문 `![대체글](주소)`로 반영), 125자 넘으면 안내만. 발행 자체는 005의 `PENDING_IMAGES` 규칙.

## R-9. 저장소 규칙 검증 (FR-011~FR-014)
- **Decision**: 테스트 컨테이너(MinIO 포크)로 서명 위조·경로 변경·Content-Type 불일치·만료·서명 없는 익명 PUT·익명 목록·`images/*` 밖 읽기 거부, `images/*` 읽기 허용을 자동 확인(003 3건 + 5건). CORS(우리 출처 PUT만)와 앱 전용 키는 저장소 서버 설정(compose `MINIO_API_CORS_ALLOW_ORIGIN`, 운영 NHN 키 발급)이라 배포 확인 항목(U-1·U-2)으로 둔다.

## 남은 확인 사항
| # | 내용 | 기본값 |
|---|---|---|
| U-1 | 운영 저장소 앱 전용 키(Put·Get·Delete만) | NHN에 발급 요청, 환경 변수 |
| U-2 | CORS 자동 검사 | compose 설정 + 수동 확인 |
| U-3 | 글 영구 삭제 때 사진 연결 해제 | 011·023이 `PostImageService.detachAll` 호출 |
