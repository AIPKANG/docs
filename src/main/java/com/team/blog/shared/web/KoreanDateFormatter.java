package com.team.blog.shared.web;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.stereotype.Component;

/** 화면용 날짜 "M월 d일"({@code Asia/Seoul} 기준). Thymeleaf에서는 {@code @koreanDateFormatter.format(...)}로 쓴다. */
@Component
public class KoreanDateFormatter {

    public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN).withZone(SEOUL);

    public static String monthDay(Instant instant) {
        return instant == null ? "" : MONTH_DAY.format(instant);
    }

    public String format(Instant instant) {
        return monthDay(instant);
    }
}
