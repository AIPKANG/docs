package com.team.blog.post.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 004 수치(헌법 II). 기본값은 {@code application.yml}의 {@code blog.post.*}(04 §2-1)와 같다.
 *
 * @param titleMaxLength   제목 코드 포인트 상한(DB {@code varchar(100)})
 * @param contentMaxLength 본문 코드 포인트 상한(DB {@code ck_post_content})
 */
@ConfigurationProperties("blog.post")
public record PostProperties(@DefaultValue("100") int titleMaxLength,
                             @DefaultValue("100000") int contentMaxLength,
                             @DefaultValue("10") int maxTags,
                             @DefaultValue Publish publish,
                             @DefaultValue Autosave autosave,
                             @DefaultValue Editor editor,
                             @DefaultValue EmptyDraftCleanup emptyDraftCleanup,
                             @DefaultValue Trash trash,
                             @DefaultValue("20") int managePageSize) {

    /** 휴지통(011, 13 §2-5). */
    public record Trash(@DefaultValue("30d") Duration retention,
                        @DefaultValue("true") boolean purgeEnabled,
                        @DefaultValue("0 20 5 * * *") String purgeCron,
                        @DefaultValue("100") int purgeBatchSize) {
    }

    /**
     * 서버 버퍼·영구 반영.
     *
     * @param bufferTtl       Redis Hash 정리용 보관 기간(저장마다 갱신)
     * @param flushInterval   영구 반영 주기
     * @param rateLimitWindow 사용자당 자동 저장 1회 창
     * @param redisRetryAfter Redis 실패 뒤 DB 경로로만 저장하는 시간
     */
    public record Autosave(@DefaultValue("24h") Duration bufferTtl,
                           @DefaultValue("true") boolean flushEnabled,
                           @DefaultValue("1m") Duration flushInterval,
                           @DefaultValue("500") int flushBatchSize,
                           @DefaultValue("50s") Duration flushLockTtl,
                           @DefaultValue("5s") Duration rateLimitWindow,
                           @DefaultValue("1048576") long maxBodyBytes,
                           @DefaultValue("30s") Duration redisRetryAfter) {
    }

    /** 브라우저 저장 간격(편집 화면에 {@code data-*}로 넘긴다). */
    public record Editor(@DefaultValue("1s") Duration localSaveDelay,
                         @DefaultValue("3s") Duration serverSaveDelay,
                         @DefaultValue("30s") Duration serverSaveMaxInterval,
                         @DefaultValue("60s") Duration retryMax,
                         @DefaultValue("7d") Duration backupTtl) {
    }

    /** 발행(005). @param idempotencyTtl 발행 요청 식별자 보관 시간(05 §6) */
    public record Publish(@DefaultValue("10m") Duration idempotencyTtl) {
    }

    /** 빈 임시글 정리(04 §2-5). */
    public record EmptyDraftCleanup(@DefaultValue("true") boolean enabled,
                                    @DefaultValue("0 40 4 * * *") String cron,
                                    @DefaultValue("24h") Duration minAge,
                                    @DefaultValue("500") int batchSize,
                                    @DefaultValue("10m") Duration lockTtl) {
    }
}
