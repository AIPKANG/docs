---
description: "008-image-upload 구현 작업 목록"
---
# Tasks: 이미지 업로드

**작업 ID**: 008은 **`T801`부터**. 003 media 클래스를 확장한다.

- [X] T801 설정 `blog.image.post.*`, `quota-bytes`, `daily-limit`, `blog.storage.type`, `ImageProperties`·`StorageProperties` 확장
- [X] T802 [P] `GifFrameCounter` + 단위 테스트
- [X] T803 `ImageStorage.read`, presign 헤더에 `Cache-Control`, `Image.postUpload`·썸네일 필드
- [X] T804 [US1·US2] `ImageUploadService` 글 사진 승인·완료 검사, `PresignResult`·`CompleteResult` 칸 추가
- [X] T805 [P] [US1·US2] `PostImageUploadIT`: 승인·두 PUT·완료·캐시 헤더·형식 위장·크기·해상도·EXIF·썸네일 규격·GIF 프레임·권한
- [X] T806 [US3] 하루 200장·1GB(advisory lock), `StorageUsageQuery`, `GET /api/me/storage`, 설정 화면 사용량
- [X] T807 [P] [US3] `StorageQuotaIT`: 동시 10건 합계 ≤1GB, 201번째 429, 실패도 셈, 사용량 API
- [X] T808 `PostImageService.sync`·`PostImageStore`, 정리 조건 `NOT EXISTS post_image`
- [X] T809 post 연결: 수동 저장·반영·발행·변경 취소 때 동기화, 발행 썸네일·남의 사진 400
- [X] T810 [P] `PostImageLinkIT`: 연결·끊김·7일 정리·남의 사진 발행 거부·카드 썸네일·작업본 중 발행본 사진 유지
- [X] T811 [US5] GIF AST 규칙 + `RENDER_VERSION` 2, `gif-play.js`, CSS, 테스트
- [X] T812 [P] `StorageRulesIT`: 서명 위조·경로 변경·만료·서명 없는 PUT 거부
- [X] T813 [P] `LocalImageStorage`·업로드 컨트롤러·`/media/**` + `LocalImageStorageIT`
- [X] T814 [US1·US4·US5] 에디터 `image-upload.js`(붙여넣기·끌어놓기·버튼·리사이즈·썸네일·오프라인 대기·재시도), 발행 설정 대체글
- [X] T815 전체 테스트·구현 메모
