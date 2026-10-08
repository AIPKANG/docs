-- 030 그룹 공개(강성찬 개인 확장, 03 ERD friend_group·group_member·post_group_visibility). 공통 테이블은 CHECK 교체만.
ALTER TABLE post DROP CONSTRAINT ck_post_visibility;
ALTER TABLE post ADD CONSTRAINT ck_post_visibility CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'GROUP', 'LINK', 'PRIVATE'));

CREATE TABLE friend_group (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    owner_id               bigint NOT NULL,
    name                   varchar(20) NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_friend_group_owner FOREIGN KEY (owner_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT uq_friend_group_name UNIQUE (owner_id, name),
    CONSTRAINT ck_friend_group_name CHECK (length(btrim(name)) BETWEEN 1 AND 20)
);

CREATE TABLE group_member (
    group_id               bigint NOT NULL,
    member_id              bigint NOT NULL,
    added_at               timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, member_id),
    CONSTRAINT fk_group_member_group FOREIGN KEY (group_id) REFERENCES friend_group (id) ON DELETE CASCADE,
    CONSTRAINT fk_group_member_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
);
CREATE INDEX ix_group_member_member ON group_member (member_id);

CREATE TABLE post_group_visibility (
    post_id                bigint NOT NULL,
    group_id               bigint NOT NULL,
    PRIMARY KEY (post_id, group_id),
    CONSTRAINT fk_post_group_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT fk_post_group_group FOREIGN KEY (group_id) REFERENCES friend_group (id) ON DELETE CASCADE
);
CREATE INDEX ix_post_group_group ON post_group_visibility (group_id);

COMMENT ON TABLE friend_group IS '친구 그룹(030)';
COMMENT ON TABLE group_member IS '그룹에 넣은 친구(030)';
COMMENT ON TABLE post_group_visibility IS '그룹 공개 글이 보이는 그룹(030)';
