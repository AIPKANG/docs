# 1. deploy.yml과 배포 순서

파일: 소스 저장소 `.github/workflows/deploy.yml` (같은 내용의 사본 `deploy/school/github-deploy.yml`). 서버 안에서 컨테이너를 바꾸는 일은 `deploy/school/run-on-server.sh`가 합니다.

## 언제 도나
| 일 | 하는 것 |
|---|---|
| `main`에 push(= PR 머지) | 테스트 → 배포 |
| `main`·`develop`으로 가는 PR | **테스트만**(배포 안 함). 깃플로우에서 머지 전에 검사하려고 1.14.0에 추가 |
| Actions 화면에서 손으로 실행 | 테스트 → 배포, 급할 때 `skip_tests`로 테스트 생략 가능 |

`concurrency`: 배포는 한 번에 하나씩 차례로(앞 배포를 취소하지 않음), PR 테스트는 PR마다 따로 돕니다.

## 순서 (push부터 사이트 반영까지, 약 6분)
1. **test 잡** (GitHub 임시 컴퓨터, 약 3분)
   - 코드 받기 → Java 21 설치(Gradle 캐시) → `./gradlew test`
   - 테스트 656개. Testcontainers가 PostgreSQL·Redis·Mailpit·MinIO를 잠깐 띄워 실제와 같은 조건으로 검사. 색을 색상 토큰 파일 밖에 쓰면 실패하는 디자인 검사도 포함
   - 하나라도 실패하면 보고서(`backend/build/reports/tests/test`)를 올리고 **여기서 멈춤** → 사이트는 옛 버전 그대로
2. **deploy 잡** (test가 성공했을 때만, PR이면 건너뜀)
   1. `Dockerfile`로 이미지 만들기: JDK 21에서 `:backend:bootJar`(frontend jar가 안에 들어감) → JRE 21 실행 이미지, root 아닌 `blog` 사용자, 비밀값은 이미지에 넣지 않음
   2. `docker save`로 `image.tar.gz` 압축 → **scp**로 학교 서버 `~/team-blog`에 이미지와 `run-on-server.sh` 복사
   3. **ssh**로 서버에서 `run-on-server.sh` 실행. Repository secrets를 환경 변수로 넘김(`GITHUB_*` 이름은 GitHub가 막아서 `GH_CLIENT_ID`로 저장하고 여기서 이름을 바꿈)
3. **서버 안 run-on-server.sh**
   - 필수 환경 변수가 비었으면 바로 중단
   - 사진 주소 서명 비밀값은 처음 한 번만 만들어 보관(바뀌면 옛 사진 주소가 깨짐)
   - `~/team-blog/app.env`(권한 0600)에 설정을 씀
   - 이미지 불러오기 → 도커 네트워크 `team-blog-net` → Redis 컨테이너는 **안 떠 있을 때만** 띄움(로그인 세션 유지)
   - 이미지를 불러오기 전에, 돌고 있던 앱의 이미지를 `team-blog:previous`로 표시해 둠(되돌릴 곳)
   - 옛 앱 컨테이너를 지우고 새 앱을 8300 → 8080으로 실행. 사진은 `team-blog-data` 볼륨이라 그대로
   - 5초마다 최대 3분 `/actuator/health` 확인 → 성공이면 안 쓰는 이미지 정리
   - **실패하면 새 버전 로그 80줄을 찍고 바로 `team-blog:previous`로 다시 켬**(1.15.0). 사이트는 전 버전으로 계속 동작하고, 새 버전이 실패한 것은 Actions에 빨간 X로 알림
4. **연결**: 학교 서버 공용 Nginx가 `https://geulgreen.java21.net`을 8300으로 넘김(배포 때 바뀌지 않음)

## 알고 있는 약점
- 앱을 한 개만 쓰기로 해서(10-13 결정) 옛 컨테이너를 지우고 새 것을 켜는 동안 수십 초 끊김. 앱 두 개를 번갈아 쓰면 없앨 수 있지만 보류([06-optimization.md](06-optimization.md))
- 마이그레이션이 적용된 뒤 새 버전이 실패해 전 버전으로 돌아가도, 마이그레이션은 "추가만" 하므로 전 버전이 그대로 켜짐
