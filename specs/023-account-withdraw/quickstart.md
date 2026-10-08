# Quickstart: 회원 탈퇴 (023)
`./gradlew test --tests '*Withdrawal*' --tests '*PermissionMatrixIT'`. 로컬: 설정 → [회원 탈퇴] → 체크·비밀번호 → 완료 화면·메일, 다른 브라우저에서 블로그 404 → 다시 로그인하면 복구 화면 → [복구하기]. 정리는 `UPDATE member SET withdrawn_at = now() - interval '31 days'` 뒤 작업 실행.
