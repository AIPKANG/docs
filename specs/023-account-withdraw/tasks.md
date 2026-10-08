---
description: "023-account-withdraw 작업 목록"
---
# Tasks: 회원 탈퇴
**작업 ID**: 023은 **`T2301`부터**.
- [X] T2301 `WithdrawalService`(안내 계산·순서·본인 확인·잠금·관리자 409·복구), 사건·리스너(세션·메일)
- [X] T2302 탈퇴 화면·완료 화면·`withdraw.js`, 복구 화면 기한·[복구하기], 메일 2종
- [X] T2303 `WithdrawalPurgeStep`·`WithdrawalPurgeJob`(회원 1명 = 트랜잭션), 단계 10~90(각 모듈)
- [X] T2304 `WithdrawalIT`, `WithdrawalPurgeFailureIT`, 매트릭스 표 9, 전체 테스트
