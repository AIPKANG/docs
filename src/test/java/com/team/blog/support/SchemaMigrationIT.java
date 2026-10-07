package com.team.blog.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Flyway V1 적용 스모크: 20개 테이블, pg_trgm, 이 기능이 기대는 제약 이름. ddl-auto=validate로 컨텍스트가 뜨면 매핑도 맞다. */
class SchemaMigrationIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void v1CreatesAllTwentyTables() {
        List<String> tables = jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'
                """, String.class);
        assertThat(tables).containsExactlyInAnyOrderElementsOf(DatabaseCleaner.TABLES);
    }

    @Test
    void pgTrgmExtensionExists() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM pg_extension WHERE extname = 'pg_trgm'", Integer.class);
        assertThat(count).isEqualTo(1);
    }

    @Test
    void memberAndAuthIdentityConstraintsExist() {
        List<String> names = jdbc.queryForList("""
                SELECT conname FROM pg_constraint
                UNION SELECT indexname FROM pg_indexes WHERE schemaname = 'public'
                """, String.class);
        assertThat(names).contains("uq_member_handle", "ck_member_handle", "uq_member_nickname", "ck_member_nickname",
                "ck_member_nickname_null", "uq_auth_identity");
    }
}
