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
| 외부 CDN 의존 | 브라우저 라이브러리도 우리 서버(/webjars/)에서 | `frontend/build.gradle.kts` |
| AI 비용·지연 | 결과를 Redis에 캐시(같은 글 30일, 비슷한 글 7일), 하루 한도, 버튼을 눌렀을 때만 | [07-ai-usage.md](07-ai-usage.md) |
| 남용 | 로그인·가입·검색·신고에 Redis 요청 제한 | `RedisRateLimiter` |
| 쓰레기 데이터 | 빈 임시글·휴지통·안 쓰는 사진·오래된 알림을 정리 작업이 지움 | `*CleanupJob`, `TrashPurgeJob` |

## 더 할 수 있는 것 (우선순위 순)
1. **무중단 배포 + 자동 되돌리기**: 지금은 옛 컨테이너를 먼저 지워 배포 중 수십 초 꺼지고, 새 버전이 안 켜지면 꺼진 채 남음. 포트 두 개(8300·8301)를 번갈아 쓰고 health가 OK일 때 Nginx를 넘기면 해결
2. **CSS·JS 캐시**: 지금은 배포 때 바뀌어서 캐시를 안 함. 파일 이름에 내용 해시를 붙이면 1년 캐시 가능(Spring `VersionResourceResolver`)
3. **이미지 WebP 변환·크기별 썸네일**로 첫 화면 전송량 줄이기
4. **GitHub Actions 테스트 시간**: 테스트 컨테이너 재사용, 병렬 실행
5. **DB 인덱스 점검**: 운영 DB(Crowfoot)는 pg_trgm 확장을 못 써서 검색이 LIKE로 돎. 글이 많아지면 전문 검색 대안 필요
