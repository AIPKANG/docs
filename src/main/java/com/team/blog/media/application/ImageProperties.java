package com.team.blog.media.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 사진 업로드·정리 수치(헌법 II: 설정값). 003은 프로필 값만 쓰고, 008이 글 사진 값(원본·썸네일·용량)을 더한다.
 *
 * @param uploadPerMinute 사용자당 1분 업로드 승인 수(04 §4-2)
 * @param profile         프로필 이미지 규격(11 §4-1)
 * @param tempTtl         연결되지 않은 TEMP 사진 보관 시간(04 §4-4)
 * @param detachedTtl     연결이 끊긴 사진 보관 시간(04 §4-4)
 * @param cleanup         정리 작업
 * @param post            글 사진 규격(008, 04 §4-1·23 §5)
 * @param quotaBytes      회원별 저장 공간(원본 + 썸네일, 23 §3)
 * @param dailyLimit      회원별 하루(한국 시간) 업로드 승인 수(23 §3)
 */
@ConfigurationProperties("blog.image")
public record ImageProperties(
        @DefaultValue("20") int uploadPerMinute,
        @DefaultValue Profile profile,
        @DefaultValue("24h") Duration tempTtl,
        @DefaultValue("7d") Duration detachedTtl,
        @DefaultValue Cleanup cleanup,
        @DefaultValue Post post,
        @DefaultValue("1073741824") long quotaBytes,
        @DefaultValue("200") int dailyLimit) {

    /**
     * 글 사진 규격.
     *
     * @param maxBytes        원본 최대 크기
     * @param thumbMaxBytes   썸네일 최대 크기
     * @param thumbMaxWidth   썸네일 최대 가로
     * @param maxDimension    원본(GIF 제외) 가로·세로 최대(초대형 거부)
     * @param gifMaxDimension GIF 가로·세로 최대
     * @param gifMaxFrames    GIF 프레임 최대
     */
    public record Post(@DefaultValue("10485760") long maxBytes,
                       @DefaultValue("1048576") long thumbMaxBytes,
                       @DefaultValue("640") int thumbMaxWidth,
                       @DefaultValue("10000") int maxDimension,
                       @DefaultValue("1920") int gifMaxDimension,
                       @DefaultValue("300") int gifMaxFrames) {
    }

    /** @param size 정확한 가로·세로(px) @param maxBytes 최대 파일 크기 */
    public record Profile(@DefaultValue("256") int size, @DefaultValue("1048576") long maxBytes) {
    }

    /** @param enabled 예약 실행 여부 @param cron 실행 시각(Asia/Seoul) @param batchSize 한 번에 볼 후보 수 */
    public record Cleanup(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("0 30 4 * * *") String cron,
            @DefaultValue("500") int batchSize) {
    }
}
