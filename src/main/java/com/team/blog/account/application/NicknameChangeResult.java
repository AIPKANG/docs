package com.team.blog.account.application;

import java.time.Instant;

/** 닉네임 변경 결과. 같은 값 재저장은 {@link Unchanged}(30일 제한을 시작하지 않는다). */
public sealed interface NicknameChangeResult {

    record Changed(Instant changedAt) implements NicknameChangeResult {
    }

    record Unchanged() implements NicknameChangeResult {
    }

    Unchanged UNCHANGED = new Unchanged();
}
