package com.team.blog.account.application;

/** 비밀번호 변경 요청. 값은 로그·응답·예외에 남기지 않는다. */
public record PasswordChangeCommand(String currentPassword, String newPassword, String newPasswordConfirm) {

    @Override
    public String toString() {
        return "PasswordChangeCommand[***]";
    }
}
