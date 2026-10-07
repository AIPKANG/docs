package com.team.blog.account.application;

import java.util.List;

/**
 * 프로필 아이콘(11 §4-3, FR-018). 이미지가 없으면 닉네임 첫 글자가 든 동그란 기본 아이콘을 화면에서 그린다(파일 저장 없음).
 * 배경색은 블로그 주소의 해시로 정한 8색 중 하나 — 사람마다 다르고 항상 같다. 8색은 모두 흰 글자와 명도 대비 4.5:1 이상이고,
 * 라이트·다크 모드 모두 같은 배경·같은 흰 글자를 쓰므로 두 모드에서 대비가 같다. 템플릿 조각: {@code fragments/avatar.html}.
 *
 * @param imageUrl 연결된 프로필 이미지 공개 주소(없으면 null)
 */
public record ProfileAvatar(String handle, String nickname, String imageUrl) {

    /** {@code .avatar-c0} ~ {@code .avatar-c7} 배경색(흰 글자 대비: 6.67, 5.08, 5.05, 5.05, 4.87, 5.36, 5.36, 6.39). */
    public static final List<String> PALETTE = List.of(
            "#0B5CAD", "#1A7F37", "#8250DF", "#BF3989", "#9A6700", "#CF222E", "#0E7490", "#57606A");

    public boolean hasImage() {
        return imageUrl != null && !imageUrl.isBlank();
    }

    /** 닉네임 첫 글자(코드 포인트 하나), 영문이면 대문자. 닉네임이 없으면(탈퇴) {@code ?}. */
    public String initial() {
        if (nickname == null || nickname.isEmpty()) {
            return "?";
        }
        int first = nickname.codePointAt(0);
        return new String(Character.toChars(Character.toUpperCase(first)));
    }

    /** 0~7. {@link String#hashCode()}는 Java 명세로 정해진 값이라 실행마다 같다. 주소가 없으면(탈퇴) 회색(7). */
    public int colorIndex() {
        return handle == null ? PALETTE.size() - 1 : Math.floorMod(handle.hashCode(), PALETTE.size());
    }

    public String colorClass() {
        return "avatar-c" + colorIndex();
    }
}
