package com.team.blog.account.domain;

/** 닉네임 오류 코드(09 §3, data-model §2.1). 검사 순서도 이 순서다. */
public enum NicknameViolation {
    NICKNAME_INVALID_FORMAT,
    NICKNAME_LETTER_REQUIRED,
    NICKNAME_RESERVED,
    NICKNAME_BANNED_WORD,
    NICKNAME_DUPLICATE
}
