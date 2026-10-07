package com.team.blog.account.application;

/** 소셜 가입 경합 번역 결과: 같은 소셜 계정이 이미 만들어졌으니 그 회원으로 로그인시킨다(001 R-8, research R-7). */
public record ExistingSocialAccount(long memberId) {
}
