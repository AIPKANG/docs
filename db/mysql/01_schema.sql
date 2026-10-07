-- =============================================================================
-- MySQL 8.0 파생본 — Crowfoot ERD / 데모용
-- -----------------------------------------------------------------------------
-- 원본: src/main/resources/db/migration/V1__common_schema.sql (PostgreSQL, 20개 테이블)
-- 실제 애플리케이션은 PostgreSQL(Flyway)로 동작한다. 이 파일은 ERD 도구·데모 용도로
-- 손으로 옮긴 복사본이며, 원본이 바뀌면 함께 고쳐야 한다. 원본과 다르면 원본이 기준이다.
--
-- 요구 버전: MySQL 8.0.16 이상(CHECK 강제), 8.0.13 이상(함수 인덱스·TEXT 식 기본값).
-- 검증: mysql:8.4 컨테이너에서 01 → 02 순서로 오류 없이 적용됨.
--
-- 변환 규칙
--   bigint GENERATED ALWAYS AS IDENTITY → BIGINT AUTO_INCREMENT
--       (MySQL은 명시적 id 삽입을 막지 않는다. PG의 ALWAYS와 다름)
--   timestamptz → DATETIME(6), 값은 항상 UTC로 저장한다(세션 time_zone='+00:00' 권장)
--   text(본문, 최대 100,000자) → MEDIUMTEXT, 기본값 '' 은 식 기본값 ('')
--   정규식 CHECK(~) → REGEXP_LIKE(..., 'c') (대소문자 구분)
--   length(btrim(x)) → CHAR_LENGTH(TRIM(x))
--   COMMENT ON → 컬럼/테이블 COMMENT 절
--   pg_trgm GIN 인덱스 → FULLTEXT ... WITH PARSER ngram
--   부분 인덱스(WHERE ...) → 같은 컬럼의 일반 인덱스 (조건은 주석으로 남김)
--   INCLUDE(views) → 인덱스 끝 컬럼으로 추가
--   varchar_pattern_ops → 일반 B-tree 인덱스 (MySQL은 LIKE 'x%'에 그대로 사용)
--
-- 콜레이션
--   테이블 기본: utf8mb4_0900_ai_ci
--   PG처럼 정확히 비교해야 하는 값은 컬럼 단위로 바꿨다.
--     - 코드값(role, status, type 등): utf8mb4_bin — CHECK IN(...)이 'user' 같은 소문자를 통과시키지 않도록
--     - handle, tag.name, email, provider_user_id, storage_key 등: utf8mb4_0900_as_cs — UNIQUE가 대소문자/악센트를 구분
--     - nickname: utf8mb4_0900_as_cs + UNIQUE((lower(nickname))) — PG의 lower() 유니크와 동일
--
-- MySQL이 표현하지 못해 빠진/바뀐 제약 (해당 위치에 주석 있음)
--   * CHECK 2개 생략 (51개 중 49개 유지):
--       comment.ck_comment_parent (parent_id <> id)
--         — MySQL은 CHECK에서 AUTO_INCREMENT 컬럼을 참조할 수 없다(ERROR 3818).
--       report_case.ck_report_case_target_ref (대상 종류와 post_id/comment_id 일치)
--         — ON DELETE SET NULL FK 컬럼은 CHECK에서 참조할 수 없다(ERROR 3823).
--   * notification.uq_notification_unread_group (부분 UNIQUE)은
--     함수 키 (receiver_id, (CASE WHEN read_at IS NULL THEN group_key END)) 로 같은 의미를 구현했다.
--   * updated_at은 PG와 같이 ON UPDATE 자동 갱신을 넣지 않았다(애플리케이션이 설정).
--   * NULL은 UNIQUE에서 서로 다른 값으로 취급된다(PG 기본 동작과 같음).
--   * 순환 FK(member.profile_image_id ↔ image.uploader_id)는 마지막 ALTER TABLE로 추가한다.
-- =============================================================================

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- 회원
CREATE TABLE member (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '회원 번호',
    handle                 VARCHAR(39) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '블로그 주소',
    nickname               VARCHAR(10) COLLATE utf8mb4_0900_as_cs NULL COMMENT '닉네임',
    nickname_changed_at    DATETIME(6) NULL COMMENT '닉네임 변경 일자',
    bio                    VARCHAR(200) NULL COMMENT '소개',
    profile_image_id       BIGINT NULL COMMENT '프로필 사진 번호',
    profile_image_url      VARCHAR(500) NULL COMMENT '프로필 사진 주소',
    role                   VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'USER' COMMENT '권한',
    status                 VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'ACTIVE' COMMENT '회원 상태',
    default_visibility     VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'PUBLIC' COMMENT '기본 공개 범위',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    updated_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정 일자',
    withdrawn_at           DATETIME(6) NULL COMMENT '탈퇴 신청 일자',
    deleted_at             DATETIME(6) NULL COMMENT '익명 처리 일자',
    PRIMARY KEY (id),
    CONSTRAINT uq_member_handle UNIQUE (handle),
    CONSTRAINT ck_member_handle CHECK (REGEXP_LIKE(handle, '^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$', 'c')),
    CONSTRAINT ck_member_nickname CHECK (REGEXP_LIKE(nickname, '^[가-힣a-zA-Z0-9]{2,10}$', 'c') AND REGEXP_LIKE(nickname, '[가-힣a-zA-Z]', 'c')),
    CONSTRAINT ck_member_bio CHECK (bio IS NULL OR CHAR_LENGTH(bio) <= 200),
    CONSTRAINT ck_member_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_member_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'WITHDRAWN')),
    CONSTRAINT ck_member_withdrawn CHECK ((status = 'WITHDRAWN') = (withdrawn_at IS NOT NULL)),
    CONSTRAINT ck_member_deleted CHECK (deleted_at IS NULL OR status = 'WITHDRAWN'),
    CONSTRAINT ck_member_nickname_null CHECK (nickname IS NOT NULL OR deleted_at IS NOT NULL),
    CONSTRAINT ck_member_default_visibility CHECK (default_visibility IN ('PUBLIC', 'PRIVATE')),
    -- PG: CREATE UNIQUE INDEX uq_member_nickname ON member (lower(nickname))
    UNIQUE INDEX uq_member_nickname ((lower(nickname))),
    -- PG: 부분 인덱스 WHERE status = 'WITHDRAWN' AND deleted_at IS NULL
    INDEX ix_member_withdraw_purge (withdrawn_at),
    -- PG: GIN gin_trgm_ops
    FULLTEXT INDEX ix_member_nickname_trgm (nickname) WITH PARSER ngram,
    FULLTEXT INDEX ix_member_handle_trgm (handle) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원';

-- 사진
CREATE TABLE image (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '사진 번호',
    uploader_id            BIGINT NOT NULL COMMENT '올린 회원 번호',
    storage_key            VARCHAR(255) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '저장 경로',
    thumb_storage_key      VARCHAR(255) COLLATE utf8mb4_0900_as_cs NULL COMMENT '썸네일 경로',
    original_name          VARCHAR(255) NOT NULL COMMENT '원래 파일 이름',
    content_type           VARCHAR(50) COLLATE utf8mb4_bin NOT NULL COMMENT '파일 형식',
    size_bytes             INT NOT NULL COMMENT '원본 크기',
    thumb_size_bytes       INT NULL COMMENT '썸네일 크기',
    width                  INT NULL COMMENT '가로',
    height                 INT NULL COMMENT '세로',
    status                 VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'TEMP' COMMENT '사진 상태',
    purpose                VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'POST' COMMENT '용도',
    detached_at            DATETIME(6) NULL COMMENT '연결 해제 일자',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    PRIMARY KEY (id),
    CONSTRAINT fk_image_uploader FOREIGN KEY (uploader_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT uq_image_storage_key UNIQUE (storage_key),
    CONSTRAINT uq_image_thumb_key UNIQUE (thumb_storage_key),
    CONSTRAINT ck_image_type CHECK (content_type IN ('image/jpeg', 'image/png', 'image/gif', 'image/webp')),
    CONSTRAINT ck_image_size CHECK (size_bytes > 0 AND size_bytes <= 10485760),
    CONSTRAINT ck_image_status CHECK (status IN ('TEMP', 'ATTACHED')),
    CONSTRAINT ck_image_purpose CHECK (purpose IN ('POST', 'PROFILE')),
    CONSTRAINT ck_image_dim CHECK ((width IS NULL OR width > 0) AND (height IS NULL OR height > 0)),
    CONSTRAINT ck_image_thumb_size CHECK (thumb_size_bytes IS NULL OR (thumb_size_bytes > 0 AND thumb_size_bytes <= 1048576)),
    INDEX ix_image_uploader (uploader_id, created_at DESC),
    -- PG: 부분 인덱스 WHERE status = 'TEMP'
    INDEX ix_image_cleanup_temp (created_at),
    -- PG: 부분 인덱스 WHERE detached_at IS NOT NULL
    INDEX ix_image_cleanup_detached (detached_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='사진';

-- 로그인 수단
CREATE TABLE auth_identity (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '로그인 수단 번호',
    member_id              BIGINT NOT NULL COMMENT '회원 번호',
    provider               VARCHAR(20) COLLATE utf8mb4_bin NOT NULL COMMENT '로그인 방식',
    provider_user_id       VARCHAR(255) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '로그인 식별값',
    email                  VARCHAR(255) COLLATE utf8mb4_0900_as_cs NULL COMMENT '이메일',
    password_hash          VARCHAR(100) COLLATE utf8mb4_bin NULL COMMENT '비밀번호 해시',
    email_verified_at      DATETIME(6) NULL COMMENT '이메일 인증 일자',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    last_login_at          DATETIME(6) NULL COMMENT '마지막 로그인 일자',
    PRIMARY KEY (id),
    CONSTRAINT fk_auth_identity_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT uq_auth_identity UNIQUE (provider, provider_user_id),
    CONSTRAINT uq_auth_identity_member UNIQUE (member_id),
    CONSTRAINT ck_auth_provider CHECK (provider IN ('LOCAL', 'GITHUB', 'GOOGLE')),
    CONSTRAINT ck_auth_password CHECK ((provider = 'LOCAL') = (password_hash IS NOT NULL)),
    -- email·provider_user_id가 대소문자 구분 콜레이션이라 email = lower(email)이 PG와 같게 동작한다.
    CONSTRAINT ck_auth_local_email CHECK (provider <> 'LOCAL' OR (email IS NOT NULL AND email = lower(email) AND provider_user_id = email)),
    -- PG: 부분 인덱스 WHERE email IS NOT NULL
    INDEX ix_auth_identity_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='로그인 수단';

-- 회원 동의
CREATE TABLE member_agreement (
    member_id              BIGINT NOT NULL COMMENT '회원 번호',
    type                   VARCHAR(20) COLLATE utf8mb4_bin NOT NULL COMMENT '동의 종류',
    agreed_at              DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '동의 일자',
    PRIMARY KEY (member_id, type),
    CONSTRAINT fk_member_agreement_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_member_agreement_type CHECK (type IN ('TERMS', 'PRIVACY', 'AI'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원 동의';

-- 태그
CREATE TABLE tag (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '태그 번호',
    name                   VARCHAR(30) COLLATE utf8mb4_0900_as_cs NOT NULL COMMENT '태그 이름',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    PRIMARY KEY (id),
    CONSTRAINT uq_tag_name UNIQUE (name),
    CONSTRAINT ck_tag_name CHECK (REGEXP_LIKE(name, '^[가-힣a-z0-9._+#-]{1,30}$', 'c') AND REGEXP_LIKE(name, '[가-힣a-z0-9]', 'c')),
    -- PG: (name varchar_pattern_ops) 접두 검색용. uq_tag_name과 중복이지만 원본 이름을 유지한다.
    INDEX ix_tag_name_prefix (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='태그';

-- 글
CREATE TABLE post (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '글 번호',
    author_id              BIGINT NOT NULL COMMENT '작성자 번호',
    title                  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '제목',
    content_md             MEDIUMTEXT NOT NULL DEFAULT ('') COMMENT '본문 원문',
    content_html           MEDIUMTEXT NOT NULL DEFAULT ('') COMMENT '렌더링 본문',
    excerpt                VARCHAR(200) NULL COMMENT '목록 요약',
    thumbnail_url          VARCHAR(500) NULL COMMENT '썸네일 주소',
    status                 VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'DRAFT' COMMENT '글 상태',
    visibility             VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'PUBLIC' COMMENT '공개 범위',
    view_count             BIGINT NOT NULL DEFAULT 0 COMMENT '조회 수',
    like_count             INT NOT NULL DEFAULT 0 COMMENT '좋아요 수',
    comment_count          INT NOT NULL DEFAULT 0 COMMENT '댓글 수',
    edit_version           BIGINT NOT NULL DEFAULT 0 COMMENT '편집 버전',
    render_version         INT NOT NULL DEFAULT 1 COMMENT '렌더링 버전',
    published_at           DATETIME(6) NULL COMMENT '최초 발행 일자',
    first_public_at        DATETIME(6) NULL COMMENT '최초 공개 일자',
    edited_at              DATETIME(6) NULL COMMENT '재발행 일자',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    updated_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정 일자',
    deleted_at             DATETIME(6) NULL COMMENT '삭제 일자',
    hidden_at              DATETIME(6) NULL COMMENT '숨김 일자',
    hidden_by              BIGINT NULL COMMENT '숨긴 관리자 번호',
    hidden_reason          VARCHAR(30) COLLATE utf8mb4_bin NULL COMMENT '숨김 사유',
    PRIMARY KEY (id),
    CONSTRAINT fk_post_author FOREIGN KEY (author_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_post_status CHECK (status IN ('DRAFT', 'PUBLISHED')),
    CONSTRAINT ck_post_visibility CHECK (visibility IN ('PUBLIC', 'PRIVATE')),
    CONSTRAINT ck_post_published CHECK (status = 'DRAFT' OR (published_at IS NOT NULL AND CHAR_LENGTH(TRIM(title)) > 0)),
    CONSTRAINT ck_post_public_at CHECK (NOT (status = 'PUBLISHED' AND visibility = 'PUBLIC') OR first_public_at IS NOT NULL),
    CONSTRAINT ck_post_edited_at CHECK (edited_at IS NULL OR (published_at IS NOT NULL AND edited_at >= published_at)),
    CONSTRAINT ck_post_content CHECK (CHAR_LENGTH(content_md) <= 100000),
    CONSTRAINT ck_post_counts CHECK (view_count >= 0 AND like_count >= 0 AND comment_count >= 0),
    CONSTRAINT fk_post_hidden_by FOREIGN KEY (hidden_by) REFERENCES member (id) ON DELETE RESTRICT,
    -- PG: ix_post_feed / ix_post_blog 는 부분 인덱스
    --     WHERE status = 'PUBLISHED' AND visibility = 'PUBLIC' AND deleted_at IS NULL AND hidden_at IS NULL
    INDEX ix_post_feed (first_public_at DESC, id DESC),
    INDEX ix_post_blog (author_id, first_public_at DESC, id DESC),
    -- PG: 부분 인덱스 WHERE deleted_at IS NULL
    INDEX ix_post_manage (author_id, status, updated_at DESC),
    -- PG: 부분 인덱스 WHERE deleted_at IS NOT NULL
    INDEX ix_post_trash (author_id, deleted_at DESC),
    -- PG: GIN gin_trgm_ops
    FULLTEXT INDEX ix_post_title_trgm (title) WITH PARSER ngram,
    FULLTEXT INDEX ix_post_content_trgm (content_md) WITH PARSER ngram
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='글';

-- 글 작업본
CREATE TABLE post_draft (
    post_id                BIGINT NOT NULL COMMENT '글 번호',
    title                  VARCHAR(100) NOT NULL DEFAULT '' COMMENT '작업 제목',
    content_md             MEDIUMTEXT NOT NULL DEFAULT ('') COMMENT '작업 본문',
    edit_version           BIGINT NOT NULL DEFAULT 0 COMMENT '편집 버전',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    updated_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정 일자',
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_draft_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT ck_post_draft_content CHECK (CHAR_LENGTH(content_md) <= 100000)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='글 작업본';

-- 글 좋아요
CREATE TABLE post_like (
    post_id                BIGINT NOT NULL COMMENT '글 번호',
    member_id              BIGINT NOT NULL COMMENT '회원 번호',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    PRIMARY KEY (post_id, member_id),
    CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_like_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    INDEX ix_post_like_member (member_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='글 좋아요';

-- 일별 조회 수
CREATE TABLE post_view_daily (
    post_id                BIGINT NOT NULL COMMENT '글 번호',
    view_date              DATE NOT NULL COMMENT '조회 일자',
    views                  INT NOT NULL COMMENT '조회 수',
    PRIMARY KEY (post_id, view_date),
    CONSTRAINT fk_post_view_daily_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT ck_post_view_daily_views CHECK (views > 0),
    -- PG: (view_date, post_id) INCLUDE (views) → MySQL은 INCLUDE가 없어 키 컬럼으로 추가
    INDEX ix_post_view_daily_date (view_date, post_id, views)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='일별 조회 수';

-- 글-태그 연결
CREATE TABLE post_tag (
    post_id                BIGINT NOT NULL COMMENT '글 번호',
    tag_id                 BIGINT NOT NULL COMMENT '태그 번호',
    position               SMALLINT NOT NULL COMMENT '입력 순서',
    PRIMARY KEY (post_id, tag_id),
    CONSTRAINT fk_post_tag_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_tag_tag FOREIGN KEY (tag_id) REFERENCES tag (id) ON DELETE RESTRICT,
    CONSTRAINT uq_post_tag_position UNIQUE (post_id, position),
    CONSTRAINT ck_post_tag_position CHECK (position >= 0 AND position < 100),
    INDEX ix_post_tag_tag (tag_id, post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='글-태그 연결';

-- 글-사진 연결
CREATE TABLE post_image (
    post_id                BIGINT NOT NULL COMMENT '글 번호',
    image_id               BIGINT NOT NULL COMMENT '사진 번호',
    PRIMARY KEY (post_id, image_id),
    CONSTRAINT fk_post_image_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_image_image FOREIGN KEY (image_id) REFERENCES image (id) ON DELETE CASCADE,
    INDEX ix_post_image_image (image_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='글-사진 연결';

-- 댓글
CREATE TABLE comment (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '댓글 번호',
    post_id                BIGINT NOT NULL COMMENT '글 번호',
    author_id              BIGINT NOT NULL COMMENT '작성자 번호',
    parent_id              BIGINT NULL COMMENT '부모 댓글 번호',
    reply_to_member_id     BIGINT NULL COMMENT '답글 대상 회원 번호',
    content                VARCHAR(1000) NOT NULL COMMENT '내용',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    updated_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정 일자',
    deleted_at             DATETIME(6) NULL COMMENT '삭제 일자',
    hidden_at              DATETIME(6) NULL COMMENT '숨김 일자',
    hidden_by              BIGINT NULL COMMENT '숨긴 관리자 번호',
    hidden_reason          VARCHAR(30) COLLATE utf8mb4_bin NULL COMMENT '숨김 사유',
    PRIMARY KEY (id),
    CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_comment_author FOREIGN KEY (author_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_comment_reply_to_member FOREIGN KEY (reply_to_member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT uq_comment_post_id UNIQUE (post_id, id),
    CONSTRAINT fk_comment_parent FOREIGN KEY (post_id, parent_id) REFERENCES comment (post_id, id) ON DELETE CASCADE,
    CONSTRAINT ck_comment_content CHECK (deleted_at IS NOT NULL OR CHAR_LENGTH(TRIM(content)) > 0),
    -- 생략: ck_comment_parent CHECK (parent_id IS NULL OR parent_id <> id)
    --   MySQL은 CHECK에서 AUTO_INCREMENT 컬럼(id)을 참조할 수 없다(ERROR 3818). 애플리케이션이 보장한다.
    CONSTRAINT ck_comment_reply_to CHECK (reply_to_member_id IS NULL OR parent_id IS NOT NULL),
    CONSTRAINT ck_comment_edited CHECK (updated_at >= created_at),
    CONSTRAINT fk_comment_hidden_by FOREIGN KEY (hidden_by) REFERENCES member (id) ON DELETE RESTRICT,
    -- PG: 부분 인덱스 WHERE parent_id IS NULL
    INDEX ix_comment_root (post_id, created_at, id),
    -- PG: 부분 인덱스 WHERE parent_id IS NOT NULL
    INDEX ix_comment_reply (parent_id, created_at, id),
    INDEX ix_comment_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='댓글';

-- 팔로우
CREATE TABLE follow (
    follower_id            BIGINT NOT NULL COMMENT '팔로우하는 회원 번호',
    followee_id            BIGINT NOT NULL COMMENT '팔로우받는 회원 번호',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    PRIMARY KEY (follower_id, followee_id),
    CONSTRAINT fk_follow_follower FOREIGN KEY (follower_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_follow_followee FOREIGN KEY (followee_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_follow_self CHECK (follower_id <> followee_id),
    INDEX ix_follow_followee (followee_id, created_at DESC),
    INDEX ix_follow_follower (follower_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='팔로우';

-- 친구 관계 (FRIENDS 공개 범위·친구 알림은 비활성)
CREATE TABLE friendship (
    member_a_id            BIGINT NOT NULL COMMENT '회원 A 번호',
    member_b_id            BIGINT NOT NULL COMMENT '회원 B 번호',
    requested_by           BIGINT NOT NULL COMMENT '요청 회원 번호',
    status                 VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '관계 상태',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    accepted_at            DATETIME(6) NULL COMMENT '수락 일자',
    PRIMARY KEY (member_a_id, member_b_id),
    CONSTRAINT fk_friendship_member_a FOREIGN KEY (member_a_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_friendship_member_b FOREIGN KEY (member_b_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_friendship_requested_by FOREIGN KEY (requested_by) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_friendship_order CHECK (member_a_id < member_b_id),
    CONSTRAINT ck_friendship_requester CHECK (requested_by IN (member_a_id, member_b_id)),
    CONSTRAINT ck_friendship_status CHECK (status IN ('PENDING', 'ACCEPTED')),
    CONSTRAINT ck_friendship_accepted CHECK ((status = 'ACCEPTED') = (accepted_at IS NOT NULL)),
    INDEX ix_friendship_b (member_b_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='친구 관계';

-- 신고 사건
CREATE TABLE report_case (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '신고 사건 번호',
    target_type            VARCHAR(20) COLLATE utf8mb4_bin NOT NULL COMMENT '신고 대상 종류',
    post_id                BIGINT NULL COMMENT '신고 글 번호',
    comment_id             BIGINT NULL COMMENT '신고 댓글 번호',
    target_author_id       BIGINT NOT NULL COMMENT '대상 작성자 번호',
    snapshot_title         VARCHAR(100) NULL COMMENT '신고 시점 제목',
    snapshot_content       VARCHAR(2000) NULL COMMENT '신고 시점 내용',
    status                 VARCHAR(20) COLLATE utf8mb4_bin NOT NULL DEFAULT 'PENDING' COMMENT '처리 상태',
    handled_by             BIGINT NULL COMMENT '처리 관리자 번호',
    handled_at             DATETIME(6) NULL COMMENT '처리 일자',
    closed_at              DATETIME(6) NULL COMMENT '종료 일자',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    PRIMARY KEY (id),
    CONSTRAINT fk_report_case_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE SET NULL,
    CONSTRAINT fk_report_case_comment FOREIGN KEY (comment_id) REFERENCES comment (id) ON DELETE SET NULL,
    CONSTRAINT fk_report_case_target_author FOREIGN KEY (target_author_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_case_handled_by FOREIGN KEY (handled_by) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_report_case_target CHECK (target_type IN ('POST', 'COMMENT')),
    -- 생략: ck_report_case_target_ref CHECK ((target_type = 'POST' AND comment_id IS NULL)
    --                                        OR (target_type = 'COMMENT' AND post_id IS NULL))
    --   post_id·comment_id가 ON DELETE SET NULL FK 컬럼이라 MySQL은 CHECK에서 참조할 수 없다.
    CONSTRAINT ck_report_case_status CHECK (status IN ('PENDING', 'HIDDEN', 'REJECTED', 'CLOSED_NO_TARGET')),
    -- PG: 부분 인덱스 WHERE status = 'PENDING'
    INDEX ix_report_case_pending (created_at),
    -- PG: 부분 인덱스 WHERE post_id IS NOT NULL / comment_id IS NOT NULL
    INDEX ix_report_case_post (post_id),
    INDEX ix_report_case_comment (comment_id),
    INDEX ix_report_case_target_author (target_author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='신고 사건';

-- 신고 (사건 1개에 신고 1건 이상)
CREATE TABLE report (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '신고 번호',
    case_id                BIGINT NOT NULL COMMENT '신고 사건 번호',
    reporter_id            BIGINT NOT NULL COMMENT '신고자 번호',
    reason                 VARCHAR(30) COLLATE utf8mb4_bin NOT NULL COMMENT '신고 사유',
    detail                 VARCHAR(200) NULL COMMENT '신고 설명',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    PRIMARY KEY (id),
    CONSTRAINT fk_report_case FOREIGN KEY (case_id) REFERENCES report_case (id) ON DELETE RESTRICT,
    CONSTRAINT fk_report_reporter FOREIGN KEY (reporter_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT uq_report_case_reporter UNIQUE (case_id, reporter_id),
    CONSTRAINT ck_report_reason CHECK (reason IN ('SPAM', 'ABUSE', 'SEXUAL', 'PRIVACY', 'COPYRIGHT', 'OTHER')),
    -- PG에서 detail이 NULL이면 length(btrim(NULL)) > 0 은 NULL → 통과한다. MySQL도 같은 3치 논리로 동작한다.
    CONSTRAINT ck_report_detail CHECK (reason <> 'OTHER' OR CHAR_LENGTH(TRIM(detail)) > 0),
    INDEX ix_report_reporter (reporter_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='신고';

-- 회원 정지 이력
CREATE TABLE member_suspension (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '정지 번호',
    member_id              BIGINT NOT NULL COMMENT '정지 회원 번호',
    reason                 VARCHAR(200) NOT NULL COMMENT '정지 사유',
    started_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '정지 시작 일자',
    ends_at                DATETIME(6) NULL COMMENT '정지 종료 일자',
    suspended_by           BIGINT NOT NULL COMMENT '정지 관리자 번호',
    lifted_at              DATETIME(6) NULL COMMENT '정지 해제 일자',
    lifted_by              BIGINT NULL COMMENT '해제 관리자 번호',
    PRIMARY KEY (id),
    CONSTRAINT fk_member_suspension_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_suspension_suspended_by FOREIGN KEY (suspended_by) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_suspension_lifted_by FOREIGN KEY (lifted_by) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_member_suspension_period CHECK (ends_at IS NULL OR ends_at > started_at),
    CONSTRAINT ck_member_suspension_lift CHECK (lifted_by IS NULL OR lifted_at IS NOT NULL),
    INDEX ix_member_suspension_member (member_id, started_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='회원 정지 이력';

-- 알림
CREATE TABLE notification (
    id                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '알림 번호',
    receiver_id            BIGINT NOT NULL COMMENT '받는 회원 번호',
    type                   VARCHAR(30) COLLATE utf8mb4_bin NOT NULL COMMENT '알림 종류',
    post_id                BIGINT NULL COMMENT '관련 글 번호',
    comment_id             BIGINT NULL COMMENT '관련 댓글 번호',
    report_id              BIGINT NULL COMMENT '관련 신고 번호',
    result                 VARCHAR(20) COLLATE utf8mb4_bin NULL COMMENT '신고 결과',
    last_actor_id          BIGINT NULL COMMENT '마지막 행동 회원 번호',
    actor_count            INT NOT NULL DEFAULT 0 COMMENT '묶인 인원 수',
    group_key              VARCHAR(100) COLLATE utf8mb4_bin NULL COMMENT '묶음 키',
    read_at                DATETIME(6) NULL COMMENT '읽은 일자',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    updated_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '수정 일자',
    PRIMARY KEY (id),
    CONSTRAINT fk_notification_receiver FOREIGN KEY (receiver_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT fk_notification_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_comment FOREIGN KEY (comment_id) REFERENCES comment (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_last_actor FOREIGN KEY (last_actor_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_notification_type CHECK (type IN ('COMMENT', 'REPLY', 'LIKE', 'FOLLOW', 'NEW_POST', 'REPORT_RESOLVED', 'CONTENT_HIDDEN')),
    CONSTRAINT ck_notification_group CHECK ((type IN ('LIKE', 'FOLLOW')) = (group_key IS NOT NULL)),
    CONSTRAINT ck_notification_result CHECK ((type = 'REPORT_RESOLVED') = (result IS NOT NULL) AND (result IS NULL OR result IN ('ACTION_TAKEN', 'NO_VIOLATION'))),
    CONSTRAINT ck_notification_count CHECK (actor_count >= 0),
    CONSTRAINT fk_notification_report FOREIGN KEY (report_id) REFERENCES report (id) ON DELETE SET NULL,
    -- PG: CREATE UNIQUE INDEX ... (receiver_id, group_key) WHERE read_at IS NULL AND group_key IS NOT NULL
    --   읽은 알림·group_key 없는 알림은 식이 NULL이 되어 서로 충돌하지 않으므로 의미가 같다.
    UNIQUE INDEX uq_notification_unread_group (receiver_id, (CAST(CASE WHEN read_at IS NULL THEN group_key END AS CHAR(100)))),
    INDEX ix_notification_list (receiver_id, updated_at DESC, id DESC),
    -- PG: 부분 인덱스 WHERE read_at IS NULL → (receiver_id, read_at)로 근사
    INDEX ix_notification_unread (receiver_id, read_at),
    -- PG: 부분 인덱스 WHERE post_id IS NOT NULL / comment_id IS NOT NULL
    INDEX ix_notification_post (post_id),
    INDEX ix_notification_comment (comment_id),
    INDEX ix_notification_cleanup (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='알림';

-- 알림에 묶인 사람
CREATE TABLE notification_actor (
    notification_id        BIGINT NOT NULL COMMENT '알림 번호',
    actor_id               BIGINT NOT NULL COMMENT '행동 회원 번호',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    PRIMARY KEY (notification_id, actor_id),
    CONSTRAINT fk_notification_actor_notification FOREIGN KEY (notification_id) REFERENCES notification (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_actor_actor FOREIGN KEY (actor_id) REFERENCES member (id) ON DELETE RESTRICT,
    INDEX ix_notification_actor_actor (actor_id, created_at DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='알림에 묶인 사람';

-- 끈 알림 종류
CREATE TABLE notification_mute (
    member_id              BIGINT NOT NULL COMMENT '회원 번호',
    type                   VARCHAR(30) COLLATE utf8mb4_bin NOT NULL COMMENT '끈 알림 종류',
    created_at             DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '생성 일자',
    PRIMARY KEY (member_id, type),
    CONSTRAINT fk_notification_mute_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_notification_mute_type CHECK (type IN ('COMMENT', 'REPLY', 'LIKE', 'FOLLOW', 'NEW_POST'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='끈 알림 종류';

-- 순환 FK: member ↔ image (image.uploader_id → member 가 먼저 생성되므로 마지막에 추가)
ALTER TABLE member ADD CONSTRAINT fk_member_profile_image
    FOREIGN KEY (profile_image_id) REFERENCES image (id) ON DELETE RESTRICT;
