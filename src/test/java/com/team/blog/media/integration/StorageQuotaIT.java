package com.team.blog.media.integration;

import static com.team.blog.support.ImageFlow.postPresignRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/** 008 T807: 회원별 저장 공간 1GB·하루 200장(US3, FR-023~FR-027). */
class StorageQuotaIT extends IntegrationTestBase {

    private static final long GB = 1_073_741_824L;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    StringRedisTemplate redis;

    private long verified(String handle) {
        return members.localMember(handle, handle, handle + "@example.com", "Blog#2026ok", true);
    }

    /** 사용량을 채우는 행(파일 없이). */
    private void fill(long memberId, long bytes) {
        long left = bytes;
        int i = 0;
        while (left > 0) {
            int chunk = (int) Math.min(left, 10_485_760);
            jdbc.update("""
                    INSERT INTO image (uploader_id, storage_key, original_name, content_type, size_bytes, status, purpose)
                    VALUES (?, ?, 'x.webp', 'image/webp', ?, 'ATTACHED', 'POST')
                    """, memberId, "images/fill/" + memberId + "-" + (i++) + ".webp", chunk);
            left -= chunk;
        }
    }

    @Test
    void concurrentPresignsNeverExceedQuota() throws Exception {
        long me = verified("quotauser");
        fill(me, GB - 5 * 1_000_000L); // 5MB 남음
        ExecutorService pool = Executors.newFixedThreadPool(10);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return mockMvc.perform(postPresignRequest(me, "image/webp", 900_000, 100_000L)).andReturn().getResponse().getStatus();
            }));
        }
        start.countDown();
        int ok = 0;
        int quota = 0;
        for (Future<Integer> f : futures) {
            int s = f.get();
            if (s == 200) {
                ok++;
            } else if (s == 409) {
                quota++;
            }
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(5);
        assertThat(quota).isEqualTo(5);
        Long used = jdbc.queryForObject("SELECT sum(size_bytes::bigint + coalesce(thumb_size_bytes,0)) FROM image WHERE uploader_id = ?",
                Long.class, me);
        assertThat(used).isLessThanOrEqualTo(GB);
        mockMvc.perform(postPresignRequest(me, "image/webp", 900_000, 100_000L))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STORAGE_QUOTA_EXCEEDED"));
    }

    @Test
    void twoHundredPerDayCountingFailures() throws Exception {
        clock.set(Instant.parse("2026-10-07T14:59:00Z")); // 한국 시간 23:59
        long me = verified("dailyuser");
        redis.opsForValue().set("img:daily:" + me + ":20261007", "199");
        // 형식 오류(승인 전 거부)는 세지 않지만, 한도 검사 뒤 공간 초과로 실패한 요청은 센다
        mockMvc.perform(postPresignRequest(me, "image/webp", 100, null)).andExpect(status().isOk());
        mockMvc.perform(postPresignRequest(me, "image/webp", 100, null))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("DAILY_UPLOAD_LIMIT"))
                .andExpect(header().string("Retry-After", "60"));
        mockMvc.perform(get("/api/me/storage").with(TestAuth.member(me)))
                .andExpect(jsonPath("$.todayCount").value(201))
                .andExpect(jsonPath("$.dailyLimit").value(200))
                .andExpect(jsonPath("$.quotaBytes").value(GB));
        // 한국 시간 0시가 지나면 새 날
        clock.set(Instant.parse("2026-10-07T15:00:01Z"));
        mockMvc.perform(postPresignRequest(me, "image/webp", 100, null)).andExpect(status().isOk());
    }

    @Test
    void usageCountsOriginalAndThumbnailOfAllImages() throws Exception {
        long me = verified("usageuser");
        jdbc.update("""
                INSERT INTO image (uploader_id, storage_key, thumb_storage_key, original_name, content_type, size_bytes,
                                   thumb_size_bytes, status, purpose)
                VALUES (?, 'images/u/1.webp', 'images/u/1_thumb.webp', 'a', 'image/webp', 1000, 200, 'TEMP', 'POST'),
                       (?, 'images/u/2.webp', NULL, 'b', 'image/webp', 300, NULL, 'ATTACHED', 'PROFILE')
                """, me, me);
        mockMvc.perform(get("/api/me/storage").with(TestAuth.member(me)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.usedBytes").value(1500));
        mockMvc.perform(get("/api/me/storage")).andExpect(status().isUnauthorized());
    }
}
