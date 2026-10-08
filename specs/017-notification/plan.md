# Implementation Plan: 인앱 알림

**Branch**: `017-notification` | **Date**: 2026-10-08 | **Spec**: [spec.md](./spec.md)

## Summary
Tier C 인앱 알림. 업무 사건을 커밋 뒤 받아(롤백이면 없음) 요청과 떨어진 한 줄 대기열에서 알림을 만든다. 만들기 전 본인·탈퇴 유예·끈 종류·읽기 권한을 처리 시점 상태로 다시 확인하고, 좋아요·팔로우는 유일 인덱스 + `notification_actor`로 묶는다. 보여줄 때 지금 상태(닉네임·제목·댓글)를 한 번의 조회로 다시 읽고, 읽을 수 없는 글은 "볼 수 없는 글이에요". 종 아이콘(30초 폴링), 펼침 목록 10개, 전체 페이지 20개씩, 읽음·모두 읽음·삭제, 종류 끄기, 90일·1,000개 정리, 탈퇴 정리 단계.

## Constitution Check
I(새 `notification` 모듈, post의 `PostAccessPolicy`·account의 `AuthorDisplay`만 씀) · II(V1 `notification`·`notification_actor`·`notification_mute` 그대로, 기준값 `blog.notification.*`, 사건 레코드는 필드 추가만) · III(본인 것만, 관리자도 404, 주소에 회원 번호 없음) · IV(문장은 서버가 만들고 화면은 `th:text`/`textContent`) · V(커밋 후·별도 스레드·실패 무시, 대기열 가득 차면 버림) · VI(동시 좋아요 10건 통합 테스트) — 위반 없음.

## Project Structure
```text
notification/application/{NotificationType, NotificationProperties, NotificationDispatcher, NotificationWriter,
    NotificationListeners, NotificationQuery, NotificationItem, NotificationPage, NotificationService,
    NotificationCleanup, NotificationCleanupJob}
notification/web/{NotificationApiController, NotificationPageController, NotificationSettingsAdvice}
shared/event/{CommentCreated(+replyTargetAuthorId), PostVisibilityChanged(+firstPublic)}
templates/notification/list.html, layout/base.html(종 아이콘), settings/settings.html(알림 칸)
static/js/notification/{bell.js, list.js}
tests: notification/integration/{NotificationIT, NotificationFailureIT}, PermissionMatrixIT §10-3
```
