package com.team.blog.post.application;

import com.team.blog.shared.error.PostContentException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * 목록 이어 보기 기준(10 §4-2): 마지막 카드의 (처음 공개 시각, 글 번호). 행이 아니라 값이라 글이 지워져도 흔들리지 않는다.
 * 겉모양은 {@code {마이크로초 epoch}_{id}}의 Base64URL(패딩 없음). 해석할 수 없으면 400 {@code INVALID_CURSOR}.
 */
public record FeedCursor(Instant firstPublicAt, long id) {

    public static final String INVALID = "INVALID_CURSOR";

    public String encode() {
        long micros = ChronoUnit.MICROS.between(Instant.EPOCH, firstPublicAt);
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((micros + "_" + id).getBytes(StandardCharsets.US_ASCII));
    }

    /** {@code null}·빈 값은 첫 페이지(빈 값을 돌려준다). */
    public static java.util.Optional<FeedCursor> decode(String raw) {
        if (raw == null || raw.isEmpty()) {
            return java.util.Optional.empty();
        }
        try {
            if (raw.length() > 64) {
                throw new IllegalArgumentException();
            }
            String text = new String(Base64.getUrlDecoder().decode(raw), StandardCharsets.US_ASCII);
            int sep = text.indexOf('_');
            long micros = Long.parseLong(text.substring(0, sep));
            long id = Long.parseLong(text.substring(sep + 1));
            if (micros < 0 || id <= 0) {
                throw new IllegalArgumentException();
            }
            return java.util.Optional.of(new FeedCursor(Instant.EPOCH.plus(micros, ChronoUnit.MICROS), id));
        } catch (RuntimeException e) {
            throw new PostContentException(INVALID);
        }
    }
}
