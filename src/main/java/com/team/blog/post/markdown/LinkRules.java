package com.team.blog.post.markdown;

import java.util.Locale;

/** 링크·이미지 주소 판정(12 §5, §6). */
final class LinkRules {

    private final String siteOrigin;
    private final String storagePrefix;

    LinkRules(String siteOrigin, String storagePrefix) {
        this.siteOrigin = trimSlash(siteOrigin);
        this.storagePrefix = storagePrefix;
    }

    /** 우리 사이트 링크: {@code /}로 시작(단 {@code //} 제외)하거나 우리 사이트 주소로 시작. */
    boolean isInternal(String href) {
        if (href == null) {
            return false;
        }
        String h = href.strip();
        if (h.startsWith("/") && !h.startsWith("//")) {
            return true;
        }
        return !siteOrigin.isEmpty() && (h.equals(siteOrigin) || h.startsWith(siteOrigin + "/"));
    }

    /** 우리 저장소 이미지 주소(공개 주소 + 버킷 + {@code images/}). */
    boolean isOwnImage(String src) {
        return src != null && storagePrefix != null && !storagePrefix.isEmpty()
                && src.startsWith(storagePrefix) && !src.contains("..")
                && !src.toLowerCase(Locale.ROOT).contains("%2e%2e");
    }

    String storagePrefix() {
        return storagePrefix;
    }

    private static String trimSlash(String value) {
        if (value == null) {
            return "";
        }
        String v = value.strip();
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
