package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.AuthorDisplay;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AuthorDisplayTest {

    @Test
    void activeMember() {
        AuthorDisplay author = AuthorDisplay.of("kim755030", "김민서", null);
        assertThat(author.fullLabel()).isEqualTo("김민서 @kim755030");
        assertThat(author.shortLabel()).isEqualTo("김민서");
        assertThat(author.blogPath()).isEqualTo("/@kim755030");
        assertThat(author.withdrawn()).isFalse();
    }

    @Test
    void withdrawingMember() {
        AuthorDisplay author = AuthorDisplay.of("kim755030", "김민서", Instant.now());
        assertThat(author.fullLabel()).isEqualTo("탈퇴한 사용자");
        assertThat(author.shortLabel()).isEqualTo("탈퇴한 사용자");
        assertThat(author.blogPath()).isNull();
    }

    @Test
    void anonymizedMember() {
        AuthorDisplay author = AuthorDisplay.of("kim755030", null, null);
        assertThat(author.fullLabel()).isEqualTo("탈퇴한 사용자");
        assertThat(author.shortLabel()).isEqualTo("탈퇴한 사용자");
        assertThat(author.blogPath()).isNull();
    }
}
