# 6. 최적화: 한 것과 더 할 수 있는 것

## 구현하면서 한 것
| 문제 | 한 것 | 어디 |
|---|---|---|
| 글쓰기 자동 저장이 DB에 계속 씀 | 자동 저장은 Redis 버퍼에 두고 1분마다 묶어서(500개씩) DB에 반영 | `AutosaveFlushJob` |
| 조회수·좋아요가 요청마다 DB 갱신 | Redis에서 세고 주기적으로 반영, 같은 사람 반복 조회는 30분 안에 한 번만 | `ViewJobs`, `LikeReconcileJob` |
| 트렌딩 계산이 무거움 | 요청 때 계산하지 않고 정해진 시각에 미리 계산해 둠 | `TrendingJob` |
| 목록 페이지가 뒤로 갈수록 느려짐 | OFFSET 대신 커서(마지막 항목 기준) 페이지 | 내 글·피드·검색 등 |
| 글 상세에서 쿼리가 늘어남(N+1) | 상세 화면 쿼리 수를 테스트로 고정(늘면 실패) | `PostDetailPageIT` |
| Markdown을 볼 때마다 변환 | 저장할 때 HTML로 변환·정화해 두고, 규칙이 바뀌면 버전 번호로 다시 만듦 | `RerenderJob`, `RENDER_VERSION` |
| 글꼴·라이브러리를 매번 받음 | 글꼴·webjars에 1년 캐시(immutable) | `StaticCacheConfig` |
| CSS·JS를 새로고침마다 다시 받음 | 주소에 내용 지문을 붙이고(`/css/site-<해시>.css`, 템플릿 `@{...}`) 1년 캐시. 내용이 바뀌면 주소가 바뀌어 바로 새로 받음 (1.16.0, 강성찬 개인 확장) | `StaticCacheConfig`, `StaticCacheIT` |
| 외부 CDN 의존 | 브라우저 라이브러리도 우리 서버(/webjars/)에서 | `frontend/build.gradle.kts` |
| AI 비용·지연 | 결과를 Redis에 캐시(같은 글 30일, 비슷한 글 7일), 하루 한도, 버튼을 눌렀을 때만 | [07-ai-usage.md](07-ai-usage.md) |
| 남용 | 로그인·가입·검색·신고에 Redis 요청 제한 | `RedisRateLimiter` |
| 쓰레기 데이터 | 빈 임시글·휴지통·안 쓰는 사진·오래된 알림을 정리 작업이 지움 | `*CleanupJob`, `TrashPurgeJob` |
| 새 버전이 안 켜지면 사이트가 꺼진 채 남음 | 배포 전 이미지를 `previous`로 표시해 두고, 3분 안에 안 켜지면 그 이미지로 다시 켬(1.15.0) | `deploy/school/run-on-server.sh` |

## 더 할 수 있는 것 (우선순위 순)
1. **무중단 배포**: 자동 되돌리기는 1.15.0에서 했고, 배포 중 수십 초 끊기는 것만 남음. 앱 두 개(blue·green)와 작은 프록시를 두고 새 앱이 정상일 때 넘기면 없앨 수 있지만, 지금은 서버(앱)를 하나만 쓰기로 해서 보류(초안은 소스 저장소 `feature/zero-downtime-deploy` 브랜치)
2. ~~**CSS·JS 캐시**~~: 1.16.0에서 함(위 표)
3. **이미지 WebP 변환·크기별 썸네일**로 첫 화면 전송량 줄이기
4. **GitHub Actions 테스트 시간**: 테스트 컨테이너 재사용, 병렬 실행
5. **DB 인덱스 점검**: 운영 DB(Crowfoot)는 pg_trgm 확장을 못 써서 검색이 LIKE로 돎. 글이 많아지면 전문 검색 대안 필요
