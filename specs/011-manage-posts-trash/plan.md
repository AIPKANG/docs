# Implementation Plan: 내 글 관리·휴지통

**Branch**: `011-manage-posts-trash` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
C-MANAGE-1·C-POST-5(Tier A). 내 글 관리를 세 탭(임시글·발행 글·휴지통) 한 줄 목록으로 만들고(20개·커서·개수·필터, SQL 1번), 삭제(빈 임시글 즉시, 그 밖 휴지통, 버퍼 반영 후 이동)·복구·영구 삭제(CASCADE·사진 연결 해제)와 30일 자동 정리를 행 잠금으로 구현한다. 스크립트가 없어도 폼으로 동작한다.

## Technical Context
Java 21 · Boot 4.1.1 · 새 의존성·마이그레이션 없음.

## Constitution Check
| 원칙 | 확인 | 전 | 후 |
|---|---|---|---|
| I | post 모듈, 사진은 media 공개 Service(`detachAll`) | PASS | PASS |
| II | 스키마 그대로, 보관 기간·크기 설정값 | PASS | PASS |
| III | 대상은 로그인 정보, 남의 글 404, 휴지통 글은 006 판정에서 제외 | PASS | PASS |
| IV | 목록 글자 이스케이프 | PASS | PASS |
| V | 버퍼 삭제는 커밋 후, 같은 삭제 반복은 같은 결과 | PASS | PASS |
| VI | 통합 테스트 | PASS | PASS |

## Project Structure
```text
post/application/{PostManageQuery(재작성), ManageRow, ManagePage, ManageTab, PostTrashService, TrashPurgeJob}
post/web/{ManagePostsController(재작성), PostTrashApiController}, templates/post/manage.html, static/js/post/manage.js
tests: post/integration/{ManagePostsIT, PostTrashIT, TrashPurgeIT}
```
