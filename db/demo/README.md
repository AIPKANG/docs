# 데모 데이터 (앱용, PostgreSQL)

발표·시연용 한국어 예시 데이터: 회원 10, 글 30, 댓글 64, 알림 103, 태그·좋아요·팔로우·신고·정지 이력 포함.
`db/mysql/02_seed.sql`(MySQL 사본용)을 `convert_mysql_seed.py`로 옮긴 것이라 내용이 같다(가상 CDN 사진만 뺐다).

## 넣기
앱을 한 번 띄워 빈 DB에 스키마(Flyway V1)가 만들어진 뒤, **비어 있는 DB에만** 넣는다.

```bash
docker compose up -d && ./gradlew bootRun     # 스키마 생성 후 그대로 둬도 된다
docker compose exec -T postgres psql -U blog -d blog -v ON_ERROR_STOP=1 < db/demo/seed.sql
```

운영 서버의 compose(`compose.prod.yaml`)에서는 `docker compose -f compose.yaml -f compose.prod.yaml exec -T postgres psql …`로 같은 파일을 넣는다. 실제 서비스 DB에는 넣지 않는다.

## 데모 계정 (비밀번호 `password1!`)
| 이메일 | 블로그 | 비고 |
|---|---|---|
| admin@example.com | @admin | 관리자(머리말 [관리]) |
| minji@example.com | @minji_dev | 글·댓글 많음 |
| hyunwoo.choi@example.com | @hyunwoo | |
| sujin@example.com | @sujin_log | 기본 공개 범위 비공개 |

그 밖의 회원은 소셜 로그인 가상 계정이라 로그인할 수 없다. `@gi-taeyang`은 정지, `@go-yerin`은 탈퇴 신청 상태다.
본문 HTML은 새벽 다시 렌더링 작업(매일 05:10)이 지금 규칙으로 새로 만든다. 그 전에도 원본 HTML로 정상 표시된다.

## 다시 만들기
`db/mysql/02_seed.sql`을 고친 뒤 `python3 db/demo/convert_mysql_seed.py`.
