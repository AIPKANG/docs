package com.team.blog.shared.text;

import java.util.List;

/**
 * 금칙어·예약어 검사용 4가지 변형(09 §4). 입력은 이미 소문자여야 한다.
 * <ol>
 *   <li>그대로</li>
 *   <li>숫자 제거</li>
 *   <li>숫자→영문: 0→o, 1→i, 3→e, 4→a, 5→s, 7→t</li>
 *   <li>1→l</li>
 * </ol>
 */
public final class TextVariants {

    private TextVariants() {
    }

    public static List<String> of(String lowerText) {
        StringBuilder noDigits = new StringBuilder(lowerText.length());
        StringBuilder leet = new StringBuilder(lowerText.length());
        StringBuilder oneToL = new StringBuilder(lowerText.length());
        for (int i = 0; i < lowerText.length(); i++) {
            char c = lowerText.charAt(i);
            if (c < '0' || c > '9') {
                noDigits.append(c);
            }
            leet.append(switch (c) {
                case '0' -> 'o';
                case '1' -> 'i';
                case '3' -> 'e';
                case '4' -> 'a';
                case '5' -> 's';
                case '7' -> 't';
                default -> c;
            });
            oneToL.append(c == '1' ? 'l' : c);
        }
        return List.of(lowerText, noDigits.toString(), leet.toString(), oneToL.toString());
    }
}
