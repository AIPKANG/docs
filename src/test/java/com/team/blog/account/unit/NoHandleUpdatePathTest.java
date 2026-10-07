package com.team.blog.account.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** 블로그 주소를 바꾸는 경로가 코드에 없음을 소스 검색으로 확인한다(SC-004, FR-011). */
class NoHandleUpdatePathTest {

    private static final Path MAIN = Path.of("src/main/java");

    private static final Pattern UPDATE_HANDLE = Pattern.compile(
            "update\\s+member\\b[^;\"]*?\\bset\\b[^;\"]*?\\bhandle\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern JPQL_UPDATE_HANDLE = Pattern.compile(
            "update\\s+Member\\b[^;\"]*?\\bset\\b[^;\"]*?\\.handle\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern HANDLE_SETTER = Pattern.compile("\\bsetHandle\\s*\\(|\\bchangeHandle\\s*\\(|\\bupdateHandle\\s*\\(");
    private static final Pattern HANDLE_ASSIGNMENT = Pattern.compile("this\\.handle\\s*=");

    @Test
    void noSqlOrJpqlUpdatesTheHandleColumn() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path file : javaFiles()) {
            String source = Files.readString(file);
            if (UPDATE_HANDLE.matcher(source).find() || JPQL_UPDATE_HANDLE.matcher(source).find()
                    || HANDLE_SETTER.matcher(source).find()) {
                offenders.add(file.toString());
            }
        }
        assertThat(offenders).isEmpty();
    }

    @Test
    void memberAssignsHandleOnlyInItsConstructor() throws IOException {
        String member = Files.readString(MAIN.resolve("com/team/blog/account/domain/Member.java"));
        Matcher matcher = HANDLE_ASSIGNMENT.matcher(member);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        assertThat(count).isEqualTo(1);
        int constructor = member.indexOf("public Member(Handle handle, Nickname nickname, Instant now)");
        int assignment = member.indexOf("this.handle =");
        int nextMethod = member.indexOf("public ", constructor + 1);
        assertThat(constructor).isPositive();
        assertThat(assignment).isBetween(constructor, nextMethod);
        for (Path file : javaFiles()) {
            if (!file.endsWith("Member.java")) {
                assertThat(HANDLE_ASSIGNMENT.matcher(Files.readString(file)).find()).as(file.toString()).isFalse();
            }
        }
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> stream = Files.walk(MAIN)) {
            return stream.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
