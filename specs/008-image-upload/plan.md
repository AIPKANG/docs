# Implementation Plan: 이미지 업로드

**Branch**: `008-image-upload` | **Date**: 2026-10-07 | **Spec**: [spec.md](./spec.md)

## Summary
C-IMG-1(Tier B). 003의 `media` 모듈을 글 사진으로 넓힌다: 원본+썸네일 두 장의 5분 서명 업로드, 완료 때 서버 재검사(형식 위장·크기·해상도·EXIF·GIF 프레임), 1분 20장·하루 200장·1GB 한도(회원별 잠금), 1년 immutable 캐시, 글 저장·반영·발행 때 `post_image` 동기화와 카드 썸네일, 남의 사진 거부, 정리 작업 보강, GIF 정지/재생 표시(렌더링 규칙 버전 2), 대체 로컬 저장소, 사용량 화면, 에디터 붙여넣기·오프라인 대기·대체글 권유.

## Technical Context
Java 21 · Boot 4.1.1 · AWS SDK v2(003) · 새 의존성·마이그레이션 없음 · MinIO 포크 Testcontainers.

## Constitution Check
| 원칙 | 확인 | 전 | 후 |
|---|---|---|---|
| I | media가 `image`·`post_image`를 소유, post는 media 공개 Service만, media는 회원 테이블 대신 advisory lock | PASS | PASS |
| II | 스키마 변경 없음, 한도 설정값 | PASS | PASS |
| III | 올리는 사람은 인증 정보, 남의 사진 연결 400, 남의 사진 complete 404 | PASS | PASS |
| IV | 우리 저장소 이미지만 표시(007), GIF도 허용 목록·CSP 그대로, 원래 파일 이름 비노출 | PASS | PASS |
| V | 저장소 호출은 트랜잭션 밖, 실패 파일은 정리 작업 재시도 | PASS | PASS |
| VI | 실제 MinIO 포크·PostgreSQL·Redis 통합 테스트 | PASS | PASS |

## Project Structure
```text
media/application/{ImageUploadService(+POST), PostImageService, StorageUsageQuery, ImageProperties(+post, quota)}
media/domain/{GifFrameCounter, Image(+postUpload, thumb)}, media/infra/{S3ImageStorage(+cache, read), LocalImageStorage, LocalUploadController, PostImageStore}
post/application/{PostDraftService·AutosaveFlusher·PostPublishService(+사진 동기화)}, post/markdown/MarkdownTransformer(+GIF)
static/js/editor/image-upload.js, static/js/gif-play.js, templates(settings 사용량, editor 사진 버튼·대체글)
tests: media/integration/{PostImageUploadIT, StorageQuotaIT, PostImageLinkIT, StorageRulesIT, LocalImageStorageIT}, media/unit/GifFrameCounterTest, post/markdown GIF
```
