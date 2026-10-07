package com.team.blog.support;

import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUserProvider;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 테스트 전용 쓰기 엔드포인트(001 T124): {@link AccountGuard#requireWritable}을 부른 뒤 행 1개({@code tag})를 만든다.
 * {@code /test/write}는 SSR 경로, {@code /api/test/write}는 REST 경로로 취급된다.
 */
@RestController
public class WriteProbeController {

    private final AccountGuard accountGuard;
    private final CurrentUserProvider currentUserProvider;
    private final JdbcTemplate jdbc;

    public WriteProbeController(AccountGuard accountGuard, CurrentUserProvider currentUserProvider, JdbcTemplate jdbc) {
        this.accountGuard = accountGuard;
        this.currentUserProvider = currentUserProvider;
        this.jdbc = jdbc;
    }

    @PostMapping({"/test/write", "/api/test/write"})
    public Map<String, Object> write() {
        long memberId = accountGuard.requireWritable(currentUserProvider.current()).memberId();
        jdbc.update("INSERT INTO tag (name) VALUES (?)", "probe" + UUID.randomUUID().toString().substring(0, 8));
        return Map.of("memberId", memberId);
    }
}
