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
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * 통합 테스트 공용 기반. 실제 PostgreSQL 18·Redis 컨테이너(정적 싱글턴)를 쓴다 — H2 금지(헌법 VI).
 * 테스트마다 DB·Redis·Mailpit을 비우고 시계를 되돌린다. 메일은 실제 SMTP로 Mailpit 컨테이너에 보낸다(001 T106).
 * 사진 저장소는 MinIO 커뮤니티 포크(04 §6-1의 고정 태그)를 띄워 실제 사전 서명 PUT으로 시험한다(003 T207).
 * 정리 작업 예약 실행은 끄고 테스트가 직접 부른다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestClockConfig.class, DatabaseCleaner.class, MemberFixtures.class})
public abstract class IntegrationTestBase {

    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> REDIS = new GenericContainer<>("redis:8").withExposedPorts(6379);

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> MAILPIT = new GenericContainer<>("axllent/mailpit")
            .withExposedPorts(1025, 8025);

    /** 003: 사진 저장소. 루트 계정은 테스트 전용 값. */
    protected static final String STORAGE_IMAGE = "pgsty/silo:RELEASE.2026-09-16T00-00-00Z";
    protected static final String STORAGE_USER = "testminio";
    protected static final String STORAGE_PASSWORD = "testminio-secret";

    @SuppressWarnings("resource")
    protected static final GenericContainer<?> STORAGE = new GenericContainer<>(STORAGE_IMAGE)
            .withCommand("server", "/data")
            .withEnv("MINIO_ROOT_USER", STORAGE_USER)
            .withEnv("MINIO_ROOT_PASSWORD", STORAGE_PASSWORD)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000).forStatusCode(200));

    static {
        POSTGRES.start();
        REDIS.start();
        MAILPIT.start();
        STORAGE.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registry.add("spring.mail.host", MAILPIT::getHost);
        registry.add("spring.mail.port", () -> MAILPIT.getMappedPort(1025));
        registry.add("blog.storage.endpoint", IntegrationTestBase::storageEndpoint);
        registry.add("blog.storage.public-base-url", IntegrationTestBase::storageEndpoint);
        registry.add("blog.storage.access-key", () -> STORAGE_USER);
        registry.add("blog.storage.secret-key", () -> STORAGE_PASSWORD);
        registry.add("blog.storage.create-bucket", () -> "true");
        registry.add("blog.image.cleanup.enabled", () -> "false");
    }

    /** 저장소 S3 API 주소. */
    protected static String storageEndpoint() {
        return "http://" + STORAGE.getHost() + ":" + STORAGE.getMappedPort(9000);
    }

    /** Mailpit HTTP API 주소. */
    protected static String mailpitApiBase() {
        return "http://" + MAILPIT.getHost() + ":" + MAILPIT.getMappedPort(8025);
    }

    protected final MailpitClient mailpit = new MailpitClient(mailpitApiBase());

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
        mailpit.deleteAll();
        clock.reset();
    }
}
