# Implementation Plan: 공개 전환 — 강성찬 개인 확장

**Spec**: [spec.md](./spec.md) | **Date**: 2026-10-09 | 선행: 025 친구 공개

## Summary

- 확인 창: 내 글 관리의 [전체 공개로], 글 상세의 공개 범위 선택에서 **전체 공개로 넓힐 때만** `window.confirm("모든 사람이 볼 수 있게 돼요. 공개할까요?")`(휴지통 확인과 같은 방식). 좁히는 쪽은 바로.
- 처음 공개되면 홈 맨 위: 공통 006 규칙 그대로(`first_public_at`은 처음 전체 공개될 때 한 번만 정해짐) — 새 코드 없음, 테스트로 확인.
- 첫 공개 응원 알림: 알림 종류 `FIRST_PUBLIC`(V3 마이그레이션, CHECK 교체만). `PostVisibilityChanged(firstPublic, from=FRIENDS)`를 받으면
  `NotificationWriter.firstPublicCheer`가 글쓴이의 친구에게 1건씩 만들고, 이어지는 `newPost`는 이미 응원을 받은 사람을 뺀다.
  "새 글" 알림을 끈 친구는 받지 않는다.

## 범위 밖(기록)

- 스크립트가 꺼진 화면의 확인 페이지: 공개 범위를 바꾸는 두 곳(내 글 관리 버튼, 글 상세 선택)은 원래 스크립트로만 동작해 따로 만들지 않았다.
