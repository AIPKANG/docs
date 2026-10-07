package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.team.blog.support.IntegrationTestBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 사용 가능 여부 API 서버 응답 p95 100ms 이내(회원 1만 행, SC-008)와 인덱스 사용 확인. */
class AvailabilityLatencyIT extends IntegrationTestBase {

    private static final int MEMBERS = 10_000;
    private static final int SAMPLES = 200;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void p95IsUnder100msWithTenThousandMembers() throws Exception {
        seed();
        // 데우기
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(fromIp(get("/api/handles/availability").param("handle", "user" + i), i)).andExpect(status().isOk());
            mockMvc.perform(fromIp(get("/api/nicknames/availability").param("nickname", "nick" + i), i)).andExpect(status().isOk());
        }
        List<Long> handleTimes = new ArrayList<>();
        List<Long> nicknameTimes = new ArrayList<>();
        for (int i = 0; i < SAMPLES; i++) {
            int n = (i * 37) % MEMBERS + 1;
            long start = System.nanoTime();
            mockMvc.perform(fromIp(get("/api/handles/availability").param("handle", "user" + n), 1000 + i))
                    .andExpect(status().isOk());
            handleTimes.add(System.nanoTime() - start);
            start = System.nanoTime();
            mockMvc.perform(fromIp(get("/api/nicknames/availability").param("nickname", "NICK" + n), 1000 + i))
                    .andExpect(status().isOk());
            nicknameTimes.add(System.nanoTime() - start);
        }
        assertThat(p95Millis(handleTimes)).as("handle p95 ms").isLessThan(100);
        assertThat(p95Millis(nicknameTimes)).as("nickname p95 ms").isLessThan(100);
    }

    @Test
    void lookupsUseTheUniqueIndexes() {
        seed();
        String handlePlan = String.join("\n", jdbc.queryForList(
                "EXPLAIN SELECT 1 FROM member WHERE handle = 'user42'", String.class));
        String handleInPlan = String.join("\n", jdbc.queryForList(
                "EXPLAIN SELECT handle FROM member WHERE handle IN ('user42', 'user42_2', 'user42_3')", String.class));
        String nicknamePlan = String.join("\n", jdbc.queryForList(
                "EXPLAIN SELECT 1 FROM member WHERE lower(nickname) = lower('NICK42') AND id <> 1", String.class));
        assertThat(handlePlan).contains("uq_member_handle");
        assertThat(handleInPlan).contains("uq_member_handle");
        assertThat(nicknamePlan).contains("uq_member_nickname");
    }

    private void seed() {
        jdbc.update("""
                INSERT INTO member (handle, nickname)
                SELECT 'user' || g, 'nick' || g FROM generate_series(1, ?) AS g
                """, MEMBERS);
        jdbc.execute("ANALYZE member");
    }

    private static MockHttpServletRequestBuilder fromIp(MockHttpServletRequestBuilder builder, int seq) {
        String ip = "10.0." + (seq / 250) + "." + (seq % 250 + 1);
        return builder.with(request -> {
            request.setRemoteAddr(ip);
            return request;
        });
    }

    private static long p95Millis(List<Long> nanos) {
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        return sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1) / 1_000_000;
    }
}
