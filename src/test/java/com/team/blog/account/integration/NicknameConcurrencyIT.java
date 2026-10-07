package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.MemberUniqueViolationTranslator;
import com.team.blog.account.application.MemberUniqueViolationTranslator.SignupContext;
import com.team.blog.account.domain.Handle;
import com.team.blog.account.domain.Member;
import com.team.blog.account.domain.Nickname;
import com.team.blog.account.domain.NicknameViolation;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.shared.error.NicknameViolationException;
import com.team.blog.support.IntegrationTestBase;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 같은 닉네임(대소문자만 다름 포함) 20건 동시 저장 → 1건만 성공(SC-003). */
class NicknameConcurrencyIT extends IntegrationTestBase {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    MemberUniqueViolationTranslator translator;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void onlyOneOfTwentyConcurrentSameNicknamesWins() throws Exception {
        String[] variants = {"Kim", "kim", "KIM"};
        int threads = 20;
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Future<String>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                Handle handle = Handle.parse("user" + i + "_x");
                Nickname nickname = Nickname.of(variants[i % variants.length]);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    try {
                        transactionTemplate.executeWithoutResult(status ->
                                memberRepository.saveAndFlush(new Member(handle, nickname, Instant.now())));
                        return "OK";
                    } catch (DataIntegrityViolationException e) {
                        try {
                            translator.translate(e, SignupContext.email(handle));
                            return "UNEXPECTED";
                        } catch (NicknameViolationException v) {
                            return v.getCode() == NicknameViolation.NICKNAME_DUPLICATE && v.isConcurrent() ? "DUP" : "OTHER";
                        }
                    }
                }));
            }
            ready.await();
            go.countDown();
            List<String> results = new ArrayList<>();
            for (Future<String> f : futures) {
                results.add(f.get());
            }
            assertThat(results).filteredOn("OK"::equals).hasSize(1);
            assertThat(results).filteredOn("DUP"::equals).hasSize(19);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM member", Integer.class)).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
