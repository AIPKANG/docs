-- 029 링크 공개(강성찬 개인 확장): 주소(비밀 열쇠)를 아는 사람만 읽는 공개 범위. 공통 테이블은 CHECK 교체만.
ALTER TABLE post DROP CONSTRAINT ck_post_visibility;
ALTER TABLE post ADD CONSTRAINT ck_post_visibility CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'LINK', 'PRIVATE'));

CREATE TABLE post_link_share (
    post_id                bigint NOT NULL,
    token                  varchar(64) NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id),
    CONSTRAINT uq_post_link_share_token UNIQUE (token),
    CONSTRAINT fk_post_link_share_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE
);

COMMENT ON TABLE post_link_share IS '링크 공개 글의 비밀 열쇠(029)';
COMMENT ON COLUMN post_link_share.token IS '주소에 붙는 열쇠(?key=), 다시 만들면 예전 주소는 막힌다';
