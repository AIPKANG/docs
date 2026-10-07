package com.team.blog.account.domain;

/** 소개 오류 코드(11 §3, data-model §3.1). 검사 순서도 이 순서다. */
public enum BioViolation {
    BIO_TOO_LONG,
    BIO_TOO_MANY_LINES,
    BIO_BANNED_WORD
}
