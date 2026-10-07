package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.domain.Member;
import com.team.blog.account.infra.MemberRepository;
import com.team.blog.support.IntegrationTestBase;
import jakarta.persistence.Column;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/** 가입 후 블로그 주소는 어떤 경로로도 바뀌지 않는다(US1-7, FR-011, SC-004). */
class HandleImmutabilityIT extends IntegrationTestBase {

    @Autowired
    MemberRepository memberRepository;

    @Autowired
    TransactionTemplate transactionTemplate;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void memberHasNoHandleMutator() throws Exception {
        assertThat(Arrays.stream(Member.class.getDeclaredMethods())
                .filter(m -> !Modifier.isPrivate(m.getModifiers()))
                .map(Method::getName)
                .filter(name -> name.toLowerCase().contains("handle")))
                .containsExactly("getHandle");
        Field handle = Member.class.getDeclaredField("handle");
        assertThat(handle.getAnnotation(Column.class).updatable()).isFalse();
    }

    @Test
    void savingOtherChangesNeverUpdatesHandle() throws Exception {
        long id = members.active("kim755030", "김민서");
        Field handleField = Member.class.getDeclaredField("handle");
        Field bioField = Member.class.getDeclaredField("bio");
        handleField.setAccessible(true);
        bioField.setAccessible(true);

        transactionTemplate.executeWithoutResult(status -> {
            Member member = memberRepository.findById(id).orElseThrow();
            try {
                handleField.set(member, "hacked");
                bioField.set(member, "소개를 바꿈");
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
            memberRepository.saveAndFlush(member);
        });

        assertThat(jdbc.queryForObject("SELECT handle FROM member WHERE id = ?", String.class, id)).isEqualTo("kim755030");
        assertThat(jdbc.queryForObject("SELECT bio FROM member WHERE id = ?", String.class, id)).isEqualTo("소개를 바꿈");
    }
}
