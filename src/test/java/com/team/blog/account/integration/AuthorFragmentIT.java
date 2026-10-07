package com.team.blog.account.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.team.blog.account.application.AuthorDisplay;
import com.team.blog.account.application.MemberSummaryQuery;
import com.team.blog.support.IntegrationTestBase;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

class AuthorFragmentIT extends IntegrationTestBase {

    @Autowired
    TemplateEngine templateEngine;

    @Autowired
    MemberSummaryQuery memberSummaryQuery;

    @Autowired
    EntityManagerFactory entityManagerFactory;

    @Test
    void rendersBylineAndNameForActiveMember() {
        String html = render(AuthorDisplay.of("kim755030", "김민서", null));
        assertThat(html).contains("<span class=\"author-nickname\">김민서</span>")
                .contains("<a class=\"author-handle\" href=\"/@kim755030\">@kim755030</a>")
                .contains("<span class=\"author-name\">김민서</span>");
    }

    @Test
    void rendersWithdrawnMemberWithoutLink() {
        String html = render(AuthorDisplay.of("kim755030", "김민서", Instant.now()));
        assertThat(html).contains("탈퇴한 사용자").doesNotContain("kim755030").doesNotContain("<a ");
    }

    @Test
    void escapesValues() {
        String html = render(new AuthorDisplay("kim", "<b>x</b>", false));
        assertThat(html).doesNotContain("<b>x</b>").contains("&lt;b&gt;x&lt;/b&gt;");
    }

    @Test
    void findByIdsUsesASingleQuery() {
        long a = members.active("kim755030", "김민서");
        long b = members.active("lee755030", "이영희");
        long c = members.withdrawing("park755030", "박철수", Instant.now());
        long d = members.anonymized("choi755030", Instant.now().minusSeconds(3600), Instant.now());
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
        Map<Long, AuthorDisplay> result = memberSummaryQuery.findByIds(List.of(a, b, c, d));
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(result.get(a).fullLabel()).isEqualTo("김민서 @kim755030");
        assertThat(result.get(b).shortLabel()).isEqualTo("이영희");
        assertThat(result.get(c).fullLabel()).isEqualTo("탈퇴한 사용자");
        assertThat(result.get(d).fullLabel()).isEqualTo("탈퇴한 사용자");
    }

    private String render(AuthorDisplay author) {
        Context context = new Context();
        context.setVariable("author", author);
        return templateEngine.process("test/author-fragments", context);
    }
}
