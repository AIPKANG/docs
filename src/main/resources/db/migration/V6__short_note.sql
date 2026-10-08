-- 033 짧은 기록(강성찬 개인 확장, 03 ERD short_note): 280자 글자만, 전체 공개·친구 공개·나만 보기.
CREATE TABLE short_note (
    id                     bigint GENERATED ALWAYS AS IDENTITY,
    author_id              bigint NOT NULL,
    content                varchar(280) NOT NULL,
    visibility             varchar(20) NOT NULL DEFAULT 'PUBLIC',
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_short_note_author FOREIGN KEY (author_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT ck_short_note_content CHECK (length(btrim(content)) BETWEEN 1 AND 280),
    CONSTRAINT ck_short_note_visibility CHECK (visibility IN ('PUBLIC', 'FRIENDS', 'PRIVATE'))
);
CREATE INDEX ix_short_note_author ON short_note (author_id, id DESC);

COMMENT ON TABLE short_note IS '짧은 기록(033)';
