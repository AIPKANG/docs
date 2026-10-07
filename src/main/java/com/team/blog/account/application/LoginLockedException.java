package com.team.blog.account.application;

/** 같은 계정(정규화 이메일) 연속 실패로 잠김(FR-025). 없는 이메일도 같은 규칙. */
public class LoginLockedException extends RuntimeException {

    public LoginLockedException() {
        super("LOGIN_LOCKED");
    }
}
