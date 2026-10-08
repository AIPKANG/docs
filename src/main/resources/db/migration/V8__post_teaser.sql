-- 035 AI 티저(강성찬 개인 확장): 글쓴이가 고른 한 줄 소개. 카드 요약 자리에 쓴다.
CREATE TABLE post_teaser (
    post_id                bigint NOT NULL,
    teaser                 varchar(100) NOT NULL,
    created_at             timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (post_id),
    CONSTRAINT fk_post_teaser_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
    CONSTRAINT ck_post_teaser CHECK (length(btrim(teaser)) BETWEEN 1 AND 100)
);
COMMENT ON TABLE post_teaser IS '글 카드에 보이는 한 줄 소개(035)';
