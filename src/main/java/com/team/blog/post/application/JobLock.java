package com.team.blog.post.application;

import java.time.Duration;

/** 서버가 여러 대여도 예약 작업을 한 번만 실행하게 하는 잠금(research R-4, U-1). */
public interface JobLock {

    /** 잠금을 얻으면 {@code task}를 실행하고 풀고 {@code true}. 다른 서버가 잡고 있으면 {@code false}. */
    boolean runExclusively(String name, Duration ttl, Runnable task);
}
