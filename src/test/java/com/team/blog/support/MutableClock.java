package com.team.blog.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** 테스트에서 시간을 고정·이동하는 시계(30일 경과 등 재현). 기본 시각은 생성 시점(마이크로초 단위로 자름). */
public class MutableClock extends Clock {

    private final Instant initial;
    private volatile Instant now;

    public MutableClock() {
        this(Instant.now().truncatedTo(ChronoUnit.MICROS));
    }

    public MutableClock(Instant initial) {
        this.initial = initial;
        this.now = initial;
    }

    public void set(Instant instant) {
        this.now = instant;
    }

    public void advance(Duration duration) {
        this.now = now.plus(duration);
    }

    public void reset() {
        this.now = initial;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        return now;
    }
}
