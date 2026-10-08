# Quickstart: 조회수 (016)
`./gradlew test --tests '*ViewCountIT' --tests '*ViewRecorderTest'`. 로컬: 비로그인으로 남의 글을 열고 1초 기다림 → 네트워크 탭 `POST …/views` 204, 1분 뒤 새로 고치면 "조회 1", 다시 열어도 그대로.
