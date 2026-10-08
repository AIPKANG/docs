# Implementation Plan: 회원 탈퇴

**Branch**: `023-account-withdraw` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
설정의 [회원 탈퇴] → 한 화면(잃는 것 안내·확인 체크·본인 확인) → 신청 즉시 상태 "탈퇴 신청"·모든 세션 종료·접수 메일, 블로그·글은 공용 규칙(006 작성자 탈퇴 유예)으로 즉시 숨김. 유예 30일 동안 로그인하면 복구 전용 화면(001)에서 [복구하기]로만 복구. 30일 뒤 매일 정리 작업이 회원 1명씩 한 트랜잭션으로 기능별 정리 단계(글 → 남의 글 댓글 → 좋아요 → 사진 → 로그인 수단 → 팔로우 → 알림 → 신고 → 회원 익명화)를 실행한다.

## Constitution Check
I(각 모듈이 `WithdrawalPurgeStep`으로 자기 데이터 단계를 제공, account는 순서만) · II(스키마 그대로, 기준값 `blog.withdraw.*`) · III(로그인 필요·인증 전 허용·관리자 409, 유예 중 복구 전용 세션) · V(세션·메일은 커밋 후, 정리 실패는 그 회원만 취소) · VI(신청·복구·정리·원자성 통합 테스트, 매트릭스 표 9) — 위반 없음.

## Project Structure
```text
account/application/{WithdrawalProperties, WithdrawalService, WithdrawalListeners, WithdrawalPurgeStep, WithdrawalPurgeJob, MemberPurgeSteps}
account/web/{WithdrawalController, AccountRestoreEntryController}
post/application/{PostPurgeStep, PostTrashService(+purgeAllByAuthor)}, interaction/application/InteractionPurgeSteps,
media/application/ImagePurgeStep, notification/application/NotificationPurgeStep, moderation/application/ReportPurgeStep
shared/event/{MemberWithdrawalRequested, MemberRestored}, shared/error/AdminCannotWithdrawException
templates/settings/withdraw.html, auth/{withdrawn, restore}.html, mail/{withdraw-requested, restored}.html, static/js/account/withdraw.js
tests: account/withdraw/{WithdrawalIT, WithdrawalPurgeFailureIT}, PermissionMatrixIT 표 9
```
