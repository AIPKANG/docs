# MySQL 8 파생 스키마 (ERD·데모용)

실제 애플리케이션은 **PostgreSQL**로 동작한다. 이 폴더는 `src/main/resources/db/migration/V1__common_schema.sql`을
MySQL 8로 옮긴 **복사본**으로, Crowfoot ERD 도구에 불러오거나 데모 데이터를 보여 줄 때만 쓴다.
원본과 내용이 다르면 원본이 기준이다. 원본을 고치면 이 파일도 함께 고쳐야 한다.

| 파일 | 내용 |
|---|---|
| `01_schema.sql` | 테이블 20개, FK 41개, CHECK 49개, 컬럼·테이블 한글 COMMENT |
| `02_seed.sql` | 한국어 데모 데이터: 회원 10, 글 30, 댓글 64, 알림 103 등 |

데모 LOCAL 계정 비밀번호는 모두 `password1!`이다(bcrypt cost 10). 관리자 계정은 `admin@example.com`이다.

## 불러오기

MySQL 8.0.16 이상이 필요하다(8.4에서 검증함).

```bash
docker run -d --name blog-mysql -e MYSQL_ROOT_PASSWORD=pw -e MYSQL_DATABASE=blog -p 53306:3306 mysql:8.4
# 기동까지 수십 초 기다린 뒤
docker exec -i blog-mysql mysql -uroot -ppw --default-character-set=utf8mb4 blog < db/mysql/01_schema.sql
docker exec -i blog-mysql mysql -uroot -ppw --default-character-set=utf8mb4 blog < db/mysql/02_seed.sql
```

반드시 `01` → `02` 순서로, 빈 데이터베이스에 적용한다.

## PostgreSQL과 다른 점

**타입·기본값**
- `GENERATED ALWAYS AS IDENTITY` → `AUTO_INCREMENT`. MySQL은 id를 직접 넣는 것을 막지 않는다.
- `timestamptz` → `DATETIME(6)`. 시간대 정보가 없으므로 항상 UTC로 저장한다.
- 본문 `text` → `MEDIUMTEXT`, 기본값 `''`은 식 기본값 `('')`.
- `updated_at`은 원본과 같이 자동 갱신(`ON UPDATE`)을 넣지 않았다.

**CHECK**
- 정규식 `~` → `REGEXP_LIKE(..., 'c')`, `length(btrim(x))` → `CHAR_LENGTH(TRIM(x))`.
- 빠진 CHECK 2개(MySQL에서 표현할 수 없음):
  - `comment.ck_comment_parent`(`parent_id <> id`): CHECK에서 AUTO_INCREMENT 컬럼을 쓸 수 없다.
  - `report_case.ck_report_case_target_ref`: `ON DELETE SET NULL` FK 컬럼은 CHECK에서 쓸 수 없다.

**콜레이션**
- 테이블 기본값은 `utf8mb4_0900_ai_ci`이다.
- 코드값 컬럼(`role`, `status`, `type` 등)은 `utf8mb4_bin`이다. `'user'` 같은 소문자 값이 CHECK를 통과하지 못하게 하려는 것이다.
- `handle`, `nickname`, `tag.name`, `email`, `provider_user_id`, `storage_key`는 `utf8mb4_0900_as_cs`이다. PG처럼 대소문자와 악센트를 구분해서 비교한다.

**인덱스**
- `uq_member_nickname`(`lower(nickname)`)은 함수 인덱스 `((lower(nickname)))`로 옮겼다.
- 부분 인덱스(`WHERE ...`)는 같은 컬럼의 일반 인덱스로 바꿨다. 원래 조건은 파일에 주석으로 남겼다.
- 부분 UNIQUE `uq_notification_unread_group`은 함수 키 `(receiver_id, CASE WHEN read_at IS NULL THEN group_key END)`로 바꿨다. 동작은 원본과 같다.
- `pg_trgm` GIN 인덱스 4개는 `FULLTEXT ... WITH PARSER ngram`으로 바꿨다. 검색은 `LIKE '%x%'`가 아니라 `MATCH ... AGAINST`로 한다.
- `INCLUDE (views)` → 키 끝 컬럼으로 추가. `varchar_pattern_ops` → 일반 인덱스.
- `pg_trgm` 확장과 `COMMENT ON` 문은 없다. COMMENT는 컬럼 정의 안에 넣었다.

**그대로인 것**
- 테이블·컬럼·제약 이름, FK의 `ON DELETE` 동작, UNIQUE에서 NULL을 서로 다른 값으로 보는 동작은 원본과 같다.
- `member.profile_image_id` ↔ `image.uploader_id` 순환 FK는 파일 끝의 `ALTER TABLE`로 추가한다.
