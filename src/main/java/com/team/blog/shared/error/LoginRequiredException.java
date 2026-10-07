package com.team.blog.shared.error;

/** 비회원이 로그인이 필요한 연산을 호출함 → 401 {@code LOGIN_REQUIRED} / SSR은 로그인 화면으로 303. */
public class LoginRequiredException extends RuntimeException {

    public static final String CODE = "LOGIN_REQUIRED";

    public LoginRequiredException() {
        super(CODE);
    }
}
