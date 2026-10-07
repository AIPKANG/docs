package com.team.blog.account.domain;

import java.util.Locale;
import java.util.random.RandomGenerator;
import java.util.regex.Pattern;

/**
 * 블로그 주소 순수 규칙(08 §2·§3).
 * <ul>
 *   <li>형식 정규식 {@link #FORMAT}은 DB {@code ck_member_handle}과 같은 문자열 상수다(설정 아님).</li>
 *   <li>{@link #bodyFromEmail}은 08 §3의 1~8단계(9단계 접두어는 {@link Handle}, 10단계 번호는 HandleService)를 구현한다.</li>
 * </ul>
 */
public final class HandleRules {

    /** DB {@code ck_member_handle}과 같은 문자열. 바꾸려면 Flyway 마이그레이션이 함께 필요하다. */
    public static final String FORMAT = "^((go|gi)-)?[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$";

    /** 본문만의 형식(접두어 없음). */
    public static final String BODY_FORMAT = "^[a-z0-9][a-z0-9_]{1,34}[a-z0-9]$";

    public static final int MIN_BODY_LENGTH = 3;
    public static final int MAX_BODY_LENGTH = 36;

    /** 08 §3 7단계 기본 자르기 길이(설정 {@code blog.account.handle.prefill-max-body-length}). */
    public static final int DEFAULT_PREFILL_MAX_BODY_LENGTH = 30;

    static final String RANDOM_PREFIX = "user_";

    private static final Pattern FORMAT_PATTERN = Pattern.compile(FORMAT);
    private static final Pattern BODY_PATTERN = Pattern.compile(BODY_FORMAT);
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^a-z0-9_]");
    private static final Pattern UNDERSCORES = Pattern.compile("_+");

    private HandleRules() {
    }

    public static boolean isValidFormat(String handle) {
        return handle != null && FORMAT_PATTERN.matcher(handle).matches();
    }

    public static boolean isValidBody(String body) {
        return body != null && BODY_PATTERN.matcher(body).matches();
    }

    /** 사용자 입력 정리: 앞뒤 공백 제거, 소문자. */
    public static String normalizeInput(String raw) {
        return raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
    }

    public static String bodyFromEmail(String email, RandomGenerator random) {
        return bodyFromEmail(email, DEFAULT_PREFILL_MAX_BODY_LENGTH, random);
    }

    /**
     * 08 §3 1~8단계. 이메일이 없으면 8단계({@code user_} + 6자리)부터.
     * <ol>
     *   <li>마지막 {@code @} 앞부분</li>
     *   <li>{@code +} 뒤 버림</li>
     *   <li>소문자</li>
     *   <li>{@code .}·{@code -} → {@code _}</li>
     *   <li>영문 소문자·숫자·{@code _} 외 제거</li>
     *   <li>연속 {@code _}는 하나로, 처음·끝 {@code _} 제거</li>
     *   <li>{@code maxLength}자로 자르고 끝 {@code _} 제거</li>
     *   <li>3자 미만이면 {@code user_} + 6자리({@code 000000}~{@code 999999})</li>
     * </ol>
     */
    public static String bodyFromEmail(String email, int maxLength, RandomGenerator random) {
        if (email == null || email.isBlank()) {
            return randomBody(random);
        }
        String local = email.strip();
        int at = local.lastIndexOf('@');
        if (at >= 0) {
            local = local.substring(0, at);
        }
        int plus = local.indexOf('+');
        if (plus >= 0) {
            local = local.substring(0, plus);
        }
        String body = local.toLowerCase(Locale.ROOT).replace('.', '_').replace('-', '_');
        body = NOT_ALLOWED.matcher(body).replaceAll("");
        body = UNDERSCORES.matcher(body).replaceAll("_");
        body = trimUnderscores(body);
        if (body.length() > maxLength) {
            body = trimTrailingUnderscores(body.substring(0, maxLength));
        }
        if (body.length() < MIN_BODY_LENGTH) {
            return randomBody(random);
        }
        return body;
    }

    /**
     * 10단계 번호 붙이기: {@code body_n}. 본문이 36자를 넘으면 본문 끝을 잘라 36자를 유지하고, 잘린 끝의 {@code _}는 지운다.
     */
    public static String withNumber(String body, int number) {
        String suffix = "_" + number;
        String base = body;
        if (base.length() + suffix.length() > MAX_BODY_LENGTH) {
            base = trimTrailingUnderscores(base.substring(0, MAX_BODY_LENGTH - suffix.length()));
        }
        return base + suffix;
    }

    static String randomBody(RandomGenerator random) {
        return RANDOM_PREFIX + String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
    }

    private static String trimUnderscores(String s) {
        int start = 0;
        while (start < s.length() && s.charAt(start) == '_') {
            start++;
        }
        return trimTrailingUnderscores(s.substring(start));
    }

    private static String trimTrailingUnderscores(String s) {
        int end = s.length();
        while (end > 0 && s.charAt(end - 1) == '_') {
            end--;
        }
        return s.substring(0, end);
    }
}
