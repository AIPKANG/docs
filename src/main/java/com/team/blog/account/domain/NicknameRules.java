package com.team.blog.account.domain;

import com.team.blog.shared.text.BannedWordFilter;
import com.team.blog.shared.text.TextVariants;
import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 닉네임 순수 규칙(09 §2~§5, research R-8·R-10·R-13). 중복 검사(⑥)만 저장소가 필요해 {@code NicknamePolicy}가 한다.
 * 형식·글자 포함 정규식은 DB {@code ck_member_nickname}과 같은 문자열 상수다.
 */
public final class NicknameRules {

    /** DB {@code ck_member_nickname} 첫 조건과 같은 문자열. */
    public static final String FORMAT = "^[가-힣a-zA-Z0-9]{2,10}$";

    /** DB {@code ck_member_nickname} 둘째 조건과 같은 문자열(한글·영문 1자 이상). */
    public static final String LETTER = "[가-힣a-zA-Z]";

    public static final int MAX_LENGTH = 10;

    private static final Pattern FORMAT_PATTERN = Pattern.compile(FORMAT);
    private static final Pattern LETTER_PATTERN = Pattern.compile(LETTER);
    private static final Pattern NOT_ALLOWED = Pattern.compile("[^가-힣a-zA-Z0-9]");

    private NicknameRules() {
    }

    /** ① 정리: 앞뒤 공백(유니코드 공백 포함) 제거 → NFC. */
    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        return Normalizer.normalize(raw.strip(), Normalizer.Form.NFC);
    }

    public static boolean isValidFormat(String value) {
        return value != null && FORMAT_PATTERN.matcher(value).matches();
    }

    public static boolean hasLetter(String value) {
        return value != null && LETTER_PATTERN.matcher(value).find();
    }

    /**
     * 예약어 포함 검사: 소문자·NFC 입력의 4변형({@link TextVariants}) 중 하나라도 예약어를 <b>포함</b>하면 참.
     * 예외 목록은 적용하지 않는다(research R-10).
     *
     * @param reservedLowerNfc 소문자·NFC로 정규화한 예약어 목록
     */
    public static boolean containsReserved(String normalized, Collection<String> reservedLowerNfc) {
        String lower = normalized.toLowerCase(Locale.ROOT);
        for (String variant : TextVariants.of(lower)) {
            for (String word : reservedLowerNfc) {
                if (!word.isEmpty() && variant.contains(word)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 정리된 값의 ②~⑤ 첫 위반. 없으면 empty. */
    public static Optional<NicknameViolation> firstViolation(String normalized, Collection<String> reservedLowerNfc,
                                                             BannedWordFilter bannedWordFilter) {
        if (!isValidFormat(normalized)) {
            return Optional.of(NicknameViolation.NICKNAME_INVALID_FORMAT);
        }
        if (!hasLetter(normalized)) {
            return Optional.of(NicknameViolation.NICKNAME_LETTER_REQUIRED);
        }
        if (containsReserved(normalized, reservedLowerNfc)) {
            return Optional.of(NicknameViolation.NICKNAME_RESERVED);
        }
        if (bannedWordFilter.containsBanned(normalized)) {
            return Optional.of(NicknameViolation.NICKNAME_BANNED_WORD);
        }
        return Optional.empty();
    }

    /** 소셜 이름 정리: NFC → 허용 문자({@code 가-힣a-zA-Z0-9}) 외 제거 → 앞 10자. */
    public static String cleanSocialName(String displayName) {
        if (displayName == null) {
            return "";
        }
        String cleaned = NOT_ALLOWED.matcher(Normalizer.normalize(displayName, Normalizer.Form.NFC)).replaceAll("");
        return cleaned.length() > MAX_LENGTH ? cleaned.substring(0, MAX_LENGTH) : cleaned;
    }

    /** 소셜 이름 정리 결과가 ②~⑤를 통과하면 그 값, 아니면 empty(중복 검사는 호출자). */
    public static Optional<String> socialNameCandidate(String displayName, Collection<String> reservedLowerNfc,
                                                       BannedWordFilter bannedWordFilter) {
        String cleaned = cleanSocialName(displayName);
        if (cleaned.isEmpty() || firstViolation(cleaned, reservedLowerNfc, bannedWordFilter).isPresent()) {
            return Optional.empty();
        }
        return Optional.of(cleaned);
    }
}
