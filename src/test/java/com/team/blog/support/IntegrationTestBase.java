package com.team.blog.support;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트 공용 기반. 실제 PostgreSQL 18·Redis 컨테이너(정적 싱글턴)를 쓴다 — H2 금지(헌법 VI).
 * 테스트마다 DB·Redis를 비우고 시계를 되돌린다. 001-auth가 Mailpit 컨테이너를 이 클래스에 추가한다(T106).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestClockConfig.class, DatabaseCleaner.class, MemberFixtures.class})
public abstract class IntegrationTestBase {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8").withExposedPorts(6379);

    static {
        POSTGRES.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected DatabaseCleaner databaseCleaner;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected MemberFixtures members;

    @AfterEach
    void cleanUpAfterEach() {
        databaseCleaner.clean();
        clock.reset();
    }
}
