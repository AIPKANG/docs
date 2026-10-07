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

    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일 HH:mm", Locale.KOREAN).withZone(SEOUL);

    /** "yyyy년 M월 d일 HH:mm"(정지 기한 안내 등). */
    public static String dateTime(Instant instant) {
        return instant == null ? "" : DATE_TIME.format(instant);
    }

    private static final DateTimeFormatter YEAR_MONTH_DAY =
            DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN).withZone(SEOUL);

    /** "yyyy년 M월 d일"(005 글 상세 발행일). */
    public static String yearMonthDay(Instant instant) {
        return instant == null ? "" : YEAR_MONTH_DAY.format(instant);
    }

    public static String monthDay(Instant instant) {
        return instant == null ? "" : MONTH_DAY.format(instant);
    }

    public String format(Instant instant) {
        return monthDay(instant);
    }
}
