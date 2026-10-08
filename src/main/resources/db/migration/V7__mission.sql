-- 034 같은 주제로 쓰기·릴레이(강성찬 개인 확장, 03 ERD mission·mission_participant).
CREATE TABLE mission (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    creator_id             bigint NOT NULL,
    title                  varchar(40) NOT NULL,
    description            varchar(500) NOT NULL DEFAULT '',
    starts_at              timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ends_at                timestamptz NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_mission_creator FOREIGN KEY (creator_id) REFERENCES member (id) ON DELETE RESTRICT,
    CONSTRAINT ck_mission_title CHECK (length(btrim(title)) BETWEEN 1 AND 40),
    CONSTRAINT ck_mission_period CHECK (ends_at > starts_at)
);
CREATE INDEX ix_mission_ends ON mission (ends_at DESC, id DESC);

CREATE TABLE mission_participant (
    mission_id             bigint NOT NULL,
    member_id              bigint NOT NULL,
    post_id                bigint NOT NULL,
    joined_at              timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (mission_id, member_id),
    CONSTRAINT uq_mission_participant_post UNIQUE (mission_id, post_id),
    CONSTRAINT fk_mission_participant_mission FOREIGN KEY (mission_id) REFERENCES mission (id) ON DELETE CASCADE,
    CONSTRAINT fk_mission_participant_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_mission_participant_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE
);
CREATE INDEX ix_mission_participant_post ON mission_participant (post_id);

COMMENT ON TABLE mission IS '같은 주제로 쓰기(034)';
COMMENT ON TABLE mission_participant IS '미션에 참여한 글, 참여 순서가 릴레이 순서(034)';
