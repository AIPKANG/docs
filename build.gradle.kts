plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.team"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    // 001-auth: OAuth2 Client(Google·GitHub), Spring Session Redis(인덱스 저장소), 메일 (Boot 4.1.1 BOM 좌표)
    implementation("org.springframework.boot:spring-boot-starter-security-oauth2-client")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    // 003-profile: 프로필 이미지 저장소(MinIO·S3 호환, SigV4 Presigned URL) — AWS SDK for Java v2
    implementation(platform("software.amazon.awssdk:bom:2.55.12"))
    implementation("software.amazon.awssdk:s3")
    // 007-content-sanitize: Markdown 렌더링(CommonMark + GFM)과 허용 목록 정화(12 §1 S-3)
    implementation("org.commonmark:commonmark:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-tables:0.30.0")
    implementation("org.commonmark:commonmark-ext-gfm-strikethrough:0.30.0")
    implementation("org.commonmark:commonmark-ext-task-list-items:0.30.0")
    implementation("org.commonmark:commonmark-ext-autolink:0.30.0")
    implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20260924.2")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Colima처럼 /var/run/docker.sock이 없는 로컬 Docker: DOCKER_HOST가 없으면 Colima 소켓을 Testcontainers에 알려 준다.
    val colimaSocket = file("${System.getProperty("user.home")}/.colima/default/docker.sock")
    if (System.getenv("DOCKER_HOST") == null && colimaSocket.exists()) {
        environment("DOCKER_HOST", "unix://${colimaSocket.absolutePath}")
        environment("TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE", "/var/run/docker.sock")
    }
}
