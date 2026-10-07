package com.team.blog.account.domain;

import com.team.blog.shared.text.BannedWordFilter;
import java.text.Normalizer;
import java.util.Optional;

/**
 * 소개 규칙(11 §3, 003 research R-3). 순수 함수.
 *
 * <p>정리 순서: 줄바꿈 통일({@code \r\n}·{@code \r} → {@code \n}) → 보이지 않는 문자·방향 문자·제어 문자 제거(헌법 IV;
 * {@code \n}은 남기고 {@code \t}는 공백으로, 이모지 결합에 쓰는 ZWJ·ZWNJ·이체 선택자는 남김) → NFC → 줄 끝 공백 제거
 * → 앞뒤 공백 제거 → 연속된 빈 줄을 하나로.
 *
 * <p>길이는 코드 포인트 수로 센다 — DB {@code ck_member_bio}의 {@code char_length}와 같은 단위다.
 */
public final class BioRules {

    private BioRules() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String unified = raw.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder cleaned = new StringBuilder(unified.length());
        unified.codePoints().forEach(cp -> {
            if (cp == '\n') {
                cleaned.append('\n');
            } else if (cp == '\t') {
                cleaned.append(' ');
            } else if (!isRemoved(cp)) {
                cleaned.appendCodePoint(cp);
            }
        });
        String nfc = Normalizer.normalize(cleaned, Normalizer.Form.NFC);
        String[] lines = nfc.split("\n", -1);
        StringBuilder out = new StringBuilder(nfc.length());
        boolean previousBlank = false;
        for (String line : lines) {
            String trimmed = line.stripTrailing();
            boolean blank = trimmed.isBlank();
            if (blank && previousBlank) {
                continue;
            }
            if (!out.isEmpty() || !blank) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(blank ? "" : trimmed);
            }
            previousBlank = blank;
        }
        return out.toString().strip();
    }

    /** 제거 대상: {@code \n}·{@code \t} 밖의 제어 문자, 영폭 공백류, 방향 문자. ZWJ(U+200D)·ZWNJ(U+200C)는 남긴다. */
    static boolean isRemoved(int cp) {
        if (Character.getType(cp) == Character.CONTROL) {
            return true;
        }
        return switch (cp) {
            case 0x200B, 0x2060, 0xFEFF, 0x00AD, // 영폭 공백·단어 결합자·BOM·소프트 하이픈
                 0x200E, 0x200F, 0x061C,         // LRM, RLM, ALM
                 0x202A, 0x202B, 0x202C, 0x202D, 0x202E,
                 0x2066, 0x2067, 0x2068, 0x2069 -> true;
            default -> false;
        };
    }

    public static int codePointLength(String value) {
        return value == null ? 0 : value.codePointCount(0, value.length());
    }

    /** 빈 값은 0줄, 그 밖은 {@code \n} 수 + 1. */
    public static int lineCount(String normalized) {
        if (normalized == null || normalized.isEmpty()) {
            return 0;
        }
        return (int) normalized.chars().filter(c -> c == '\n').count() + 1;
    }

    /** 길이 → 줄 수 → 금칙어 순으로 첫 위반. 입력은 {@link #normalize} 결과여야 한다. */
    public static Optional<BioViolation> firstViolation(String normalized, int maxLength, int maxLines,
                                                        BannedWordFilter bannedWordFilter) {
        if (codePointLength(normalized) > maxLength) {
            return Optional.of(BioViolation.BIO_TOO_LONG);
        }
        if (lineCount(normalized) > maxLines) {
            return Optional.of(BioViolation.BIO_TOO_MANY_LINES);
        }
        if (bannedWordFilter.containsBanned(normalized)) {
            return Optional.of(BioViolation.BIO_BANNED_WORD);
        }
        return Optional.empty();
    }
}
