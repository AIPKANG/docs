# Quickstart: 신고·숨김·정지 (022)
`./gradlew test --tests '*ModerationIT' --tests '*PermissionMatrixIT'`. 로컬: 다른 회원 글에서 [신고] → 관리자 계정(`UPDATE member SET role='ADMIN'`)으로 머리말 [관리] → 대기 탭 → 숨기기 → 신고자·작성자 알림, 글은 작성자에게만 안내와 함께 보임 → 처리됨 탭 [숨김 해제]. [회원 관리]에서 정지·해제.
