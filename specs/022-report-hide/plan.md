# Implementation Plan: 신고·숨김·정지

**Branch**: `022-report-hide` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
회원이 글·댓글을 신고(사유 6종, 기타 설명 필수, 같은 대상 재신고는 성공만, 1분 5·하루 50)하면 대상별 대기 묶음에 모이고 신고 시점 내용을 복사해 둔다. 관리자는 대기·처리됨 탭에서 복사본만 보고 숨기기(사유) 또는 문제없음으로 대기 신고를 한 번에 닫으며, 신고자마다 처리 결과 알림·작성자에게 숨김 알림(신고자·관리자 정보 없음)이 커밋 뒤 나간다. 숨긴 글은 작성자 외 모두에게 비공개 글과 같고(읽기 판정·공용 목록 조건), 숨긴 댓글은 자리만 남고 댓글 수에서 빠진다. 해제는 원래대로, 알림 없음. 회원 정지(1·7·30일·영구, 사유)는 즉시 세션을 끊고 기간이 지나면 로그인 때 자동으로 풀린다.

## Constitution Check
I(새 `moderation` 모듈, post의 `PostReadAccess`·`PostCounters`, account의 `SessionRevoker`만) · II(V1 `report_case`·`report`·`member_suspension`·`hidden_*` 그대로, 기준값 `blog.moderation.*`) · III(관리자 아니면 404, 숨김은 006 판정의 한 단계, 관리자도 남의 비공개 원문 못 봄, 자기 것 처리 불가) · IV(복사본·사유는 `th:text`) · V(알림은 커밋 후, 실패해도 처리 성공) · VI(동시 첫 신고·처리·숨김 노출 통합 테스트, 매트릭스 표 5·6) — 위반 없음.

## Project Structure
```text
moderation/application/{ModerationProperties, ReportReason, AdminGuard, ReportService, ModerationService, SuspensionService,
    ModerationQuery, ReportCleanupJob}
moderation/web/{ReportApiController, AdminController, HiddenNoticeSection}
post/application/{PostAccessPolicy(+숨김 단계·공용 조건), visibility/PostFacts(+hidden), PostDetail(+hiddenReason)}
notification/application/NotificationListeners(+ContentHidden, ReportsResolved), shared/event/{ContentHidden, ReportsResolved}
templates/admin/{reports, report, members}.html, post/detail.html(숨김 안내·신고 창), static/js/post/report.js
tests: moderation/integration/ModerationIT, PermissionMatrixIT 표 5·6
```
