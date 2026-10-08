# Implementation Plan: 댓글·답글

**Branch**: `014-comment` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
C-CMT-1(Tier B). 1단계 댓글(답글은 항상 최상위 아래, 답글의 답글은 대상 회원 기록)을 새 `interaction` 모듈에 만든다: 정해진 검사 순서의 작성(10초 중복·1분 10개), 페이지당 SQL 2번 조회(최상위 20 + 답글 3, 상태별 표시·비노출), 수정(숨김 409), 삭제(자리 남기기·자리 정리·댓글 수 규칙), 특정 댓글부터 보기, 글 상세 SSR 첫 20개와 스크립트 없는 더 보기·작성.

## Constitution Check
| 원칙 | 확인 | 결과 |
|---|---|---|
| I | interaction → post 공개 Service(`PostReadAccess`, `PostCounters`), 사건은 shared.event | PASS |
| II | 스키마 그대로(V1이 21 §15 반영), 제한 수치 설정값 | PASS |
| III | 글 읽기 판정 006 한 곳, 남의 댓글 수정·삭제 404, 글 주인·관리자도 남의 댓글 삭제 404 | PASS |
| IV | 댓글은 글자로만(이스케이프 + pre-line), 링크 변환 없음 | PASS |
| V | 사건 커밋 후, 중복 요청은 같은 결과 | PASS |
| VI | 통합 테스트 + 012 매트릭스 표 3 | PASS |

## Project Structure
```text
interaction/{application/{CommentService, CommentQuery, CommentView, CommentPage, CommentContentRules, CommentProperties},
             web/{CommentApiController, CommentFormController}}
post/application/{PostReadAccess, PostCounters}, shared/event/{CommentCreated, CommentDeleted}, shared/error/CommentHiddenException
templates/post/detail.html(댓글 영역)·fragments/comment.html, static/js/post/comments.js
tests: interaction/{unit/CommentContentRulesTest, integration/{CommentWriteIT, CommentReadIT, CommentDeleteIT}}, PermissionMatrixIT 표 3
```
