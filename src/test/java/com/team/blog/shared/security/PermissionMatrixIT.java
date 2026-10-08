package com.team.blog.shared.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.team.blog.support.IntegrationTestBase;
import com.team.blog.support.TestAuth;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/**
 * 012 권한 매트릭스(42 §5·§9·§10, FR-032): 구현된 기능의 표 1·2·7·8 모든 칸. 표 3~6·9는 해당 기능이 행을 더한다.
 * 017은 §10-3 알림 행("작성자" 칸 = 받은 사람 본인)을, 018은 §10-1 팔로우 행("작성자" 칸 = 대상 본인)을 더했다.
 * 행위자: 비회원, 인증 전 회원, 회원(남), 작성자, 관리자(남). 404는 없는 글과 본문이 같아야 한다.
 */
class PermissionMatrixIT extends IntegrationTestBase {

    private static final Instant T = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    JdbcTemplate jdbc;

    private long author;
    private long other;
    private long unverified;
    private long admin;
    private Map<String, RequestPostProcessor> actors;

    @BeforeEach
    void actors() {
        author = members.localMember("mxauthor", "mxauthor", "mxauthor@example.com", "Blog#2026ok", true);
        other = members.localMember("mxother", "mxother", "mxother@example.com", "Blog#2026ok", true);
        unverified = members.localMember("mxunver", "mxunver", "mxunver@example.com", "Blog#2026ok", false);
        admin = members.localMember("mxadmin", "mxadmin", "mxadmin@example.com", "Blog#2026ok", true);
        jdbc.update("UPDATE member SET role = 'ADMIN' WHERE id = ?", admin);
        actors = new LinkedHashMap<>();
        actors.put("비회원", null);
        actors.put("인증 전", TestAuth.member(unverified));
        actors.put("회원", TestAuth.member(other));
        actors.put("작성자", TestAuth.member(author));
        actors.put("관리자", TestAuth.admin(admin));
    }

    private MockHttpServletResponse run(MockHttpServletRequestBuilder request, RequestPostProcessor actor) throws Exception {
        return mockMvc.perform(actor == null ? request : request.with(actor)).andReturn().getResponse();
    }

    private static String normalized(MockHttpServletResponse r) throws Exception {
        return r.getContentAsString().replaceAll("(name=\"_csrf\" (content|value)=\")[^\"]*", "$1");
    }

    /** 행위자별 기대 상태 코드(순서: 비회원, 인증 전, 회원, 작성자, 관리자). 각 칸마다 새 대상을 만든다. */
    private void row(String action, LongFunction<MockHttpServletRequestBuilder> request, java.util.function.LongSupplier target,
                     int... expected) throws Exception {
        int i = 0;
        for (Map.Entry<String, RequestPostProcessor> actor : actors.entrySet()) {
            long id = target.getAsLong();
            int status = run(request.apply(id), actor.getValue()).getStatus();
            assertThat(status).as("%s / %s", action, actor.getKey()).isEqualTo(expected[i++]);
        }
    }

    // ----- 표 1: 글 상세 보기 -----

    @Test
    void table1PostDetail() throws Exception {
        long pub = posts.published(author, "공개", "본문", 1, T);
        long priv = posts.published(author, "비공개", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        long editing = posts.published(author, "수정 중", "본문", 1, T);
        posts.workingCopy(editing, "작업본 비밀", "작업본 내용", 2);
        long draft = posts.draft(author, "임시", "본문", 0);
        long trashed = posts.published(author, "휴지통", "본문", 1, T);
        posts.trash(trashed);

        Map<Long, int[]> expected = new LinkedHashMap<>();
        expected.put(pub, new int[] {200, 200, 200, 200, 200});
        expected.put(priv, new int[] {404, 404, 404, 200, 404});
        expected.put(editing, new int[] {200, 200, 200, 200, 200});
        expected.put(draft, new int[] {404, 404, 404, 302, 404}); // 작성자는 에디터로(010)
        expected.put(trashed, new int[] {404, 404, 404, 404, 404});
        for (Map.Entry<Long, int[]> e : expected.entrySet()) {
            int i = 0;
            for (Map.Entry<String, RequestPostProcessor> actor : actors.entrySet()) {
                MockHttpServletResponse r = run(get("/@mxauthor/posts/" + e.getKey()), actor.getValue());
                assertThat(r.getStatus()).as("글 %d / %s", e.getKey(), actor.getKey()).isEqualTo(e.getValue()[i++]);
                if (r.getStatus() == 404) {
                    assertThat(normalized(r)).isEqualTo(normalized(run(get("/@mxauthor/posts/999999"), actor.getValue())));
                }
                if (e.getKey() == editing && r.getStatus() == 200) {
                    assertThat(r.getContentAsString()).doesNotContain("작업본 내용");
                }
            }
        }
        // 작성자 탈퇴 유예: 모두 404
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", author);
        for (Map.Entry<String, RequestPostProcessor> actor : actors.entrySet()) {
            if (!actor.getKey().equals("작성자")) {
                assertThat(run(get("/@mxauthor/posts/" + pub), actor.getValue()).getStatus()).as(actor.getKey()).isEqualTo(404);
            }
        }
    }

    // ----- 표 2: 글 쓰기 행동 -----

    private static String saveBody(long base) {
        return "{\"title\":\"t\",\"contentMd\":\"c\",\"baseVersion\":" + base + "}";
    }

    @Test
    void table2PostWriteActions() throws Exception {
        // 새 글 만들기: 비회원 401, 인증 전 403, 작성자 ✅
        assertThat(run(post("/api/posts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"), null).getStatus()).isEqualTo(401);
        assertThat(run(post("/api/posts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"), TestAuth.member(unverified)).getStatus()).isEqualTo(403);
        assertThat(run(post("/api/posts").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}"), TestAuth.member(author)).getStatus()).isEqualTo(201);

        row("자동 저장", id -> put("/api/posts/" + id + "/autosave").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(saveBody(0)),
                () -> posts.draft(author, "", "", 0), 401, 403, 404, 200, 404);
        row("수동 저장", id -> put("/api/posts/" + id + "/draft").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(saveBody(0)),
                () -> posts.draft(author, "", "", 0), 401, 403, 404, 200, 404);
        row("발행", id -> post("/api/posts/" + id + "/publish").with(csrf()).header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"t\",\"contentMd\":\"c\",\"tags\":[],\"visibility\":\"PUBLIC\",\"baseVersion\":0}"),
                () -> posts.draft(author, "", "", 0), 401, 403, 404, 200, 404);
        row("변경 취소", id -> delete("/api/posts/" + id + "/working-copy").with(csrf()),
                () -> {
                    long id = posts.published(author, "발행", "본문", 1, T);
                    posts.workingCopy(id, "작업본", "", 2);
                    return id;
                }, 401, 403, 404, 204, 404);
        row("공개 범위 변경", id -> patch("/api/posts/" + id + "/visibility").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"visibility\":\"PRIVATE\"}"),
                () -> posts.published(author, "발행", "본문", 1, T), 401, 403, 404, 200, 404);
        row("삭제(남의 글)", id -> delete("/api/posts/" + id).with(csrf()),
                () -> posts.published(author, "발행", "본문", 1, T), 401, 404, 404, 200, 404);
        row("복구", id -> post("/api/posts/" + id + "/restore").with(csrf()),
                () -> {
                    long id = posts.published(author, "휴지통", "본문", 1, T);
                    posts.trash(id);
                    return id;
                }, 401, 404, 404, 200, 404);
        row("영구 삭제", id -> delete("/api/posts/" + id + "/permanent").with(csrf()),
                () -> {
                    long id = posts.published(author, "휴지통", "본문", 1, T);
                    posts.trash(id);
                    return id;
                }, 401, 404, 404, 204, 404);
        // 휴지통 글이 아니면 복구·영구 삭제는 작성자도 404
        long live = posts.published(author, "살아 있음", "본문", 1, T);
        assertThat(run(post("/api/posts/" + live + "/restore").with(csrf()), TestAuth.member(author)).getStatus()).isEqualTo(404);
        assertThat(run(delete("/api/posts/" + live + "/permanent").with(csrf()), TestAuth.member(author)).getStatus()).isEqualTo(404);
        // 인증 전 회원도 자기 글은 삭제·복구·영구 삭제 가능
        long own = posts.published(unverified, "인증 전 글", "본문", 1, T);
        assertThat(run(delete("/api/posts/" + own).with(csrf()), TestAuth.member(unverified)).getStatus()).isEqualTo(200);
        assertThat(run(post("/api/posts/" + own + "/restore").with(csrf()), TestAuth.member(unverified)).getStatus()).isEqualTo(200);
        assertThat(run(delete("/api/posts/" + own).with(csrf()), TestAuth.member(unverified)).getStatus()).isEqualTo(200);
        assertThat(run(delete("/api/posts/" + own + "/permanent").with(csrf()), TestAuth.member(unverified)).getStatus()).isEqualTo(204);
    }

    // ----- 표 3: 댓글(014) -----

    private long commentBy(long post, long who, String content) {
        return jdbc.queryForObject("INSERT INTO comment (post_id, author_id, content) VALUES (?, ?, ?) RETURNING id",
                Long.class, post, who, content);
    }

    @Test
    void table3Comments() throws Exception {
        long post = posts.published(author, "댓글 글", "본문", 1, T);
        long priv = posts.published(author, "비공개", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        // 보기: 글을 볼 수 있으면
        row("댓글 보기(공개 글)", id -> get("/api/posts/" + id + "/comments"), () -> post, 200, 200, 200, 200, 200);
        row("댓글 보기(비공개 글)", id -> get("/api/posts/" + id + "/comments"), () -> priv, 404, 404, 404, 200, 404);
        // 쓰기·답글: 비회원 401, 인증 전 403, 회원·작성자·관리자 ✅
        row("쓰기", id -> post("/api/posts/" + id + "/comments").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"댓글 " + UUID.randomUUID() + "\"}"), () -> post, 401, 403, 201, 201, 201);
        // 수정: 댓글 작성자만(글 주인·관리자도 404). 대상은 "회원"이 쓴 댓글
        row("수정", id -> patch("/api/comments/" + id).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"고침\"}"), () -> commentBy(post, other, "회원 댓글"), 401, 403, 200, 404, 404);
        // 삭제: 본인 것만, 인증 전도 본인 것은 가능 — 남의 것은 404
        row("삭제", id -> delete("/api/comments/" + id).with(csrf()), () -> commentBy(post, other, "회원 댓글"),
                401, 404, 204, 404, 404);
        long own = commentBy(post, unverified, "인증 전 회원 댓글");
        assertThat(run(delete("/api/comments/" + own).with(csrf()), TestAuth.member(unverified)).getStatus()).isEqualTo(204);
    }

    // ----- 표 4: 좋아요(015) -----

    @Test
    void table4Likes() throws Exception {
        long post = posts.published(author, "좋아요 글", "본문", 1, T);
        long priv = posts.published(author, "비공개", "본문", 1, T);
        jdbc.update("UPDATE post SET visibility = 'PRIVATE' WHERE id = ?", priv);
        row("누르기", id -> put("/api/posts/" + id + "/like").with(csrf()), () -> post, 401, 403, 200, 400, 200);
        row("취소", id -> delete("/api/posts/" + id + "/like").with(csrf()), () -> post, 401, 403, 200, 400, 200);
        row("볼 수 없는 글", id -> put("/api/posts/" + id + "/like").with(csrf()), () -> priv, 401, 403, 404, 400, 404);
    }

    // ----- §10-3: 알림(017) — "작성자" 칸이 받은 사람 본인 -----

    private long notificationFor(long receiver) {
        return jdbc.queryForObject("""
                INSERT INTO notification (receiver_id, type, result, actor_count) VALUES (?, 'REPORT_RESOLVED', 'NO_VIOLATION', 0)
                RETURNING id
                """, Long.class, receiver);
    }

    @Test
    void section10_3Notifications() throws Exception {
        row("목록", id -> get("/api/notifications"), () -> 0, 401, 200, 200, 200, 200);
        row("안 읽은 수", id -> get("/api/notifications/unread-count"), () -> 0, 401, 200, 200, 200, 200);
        row("읽음", id -> patch("/api/notifications/" + id + "/read").with(csrf()), () -> notificationFor(author),
                401, 404, 404, 204, 404);
        row("삭제", id -> delete("/api/notifications/" + id).with(csrf()), () -> notificationFor(author),
                401, 404, 404, 204, 404);
        row("모두 읽음", id -> post("/api/notifications/read-all").with(csrf()), () -> 0, 401, 200, 200, 200, 200);
        row("종류 끄기", id -> put("/api/me/notification-settings").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"LIKE\":false}"), () -> 0, 401, 200, 200, 200, 200);
        assertThat(run(get("/notifications"), null).getStatus()).isEqualTo(303);
    }

    // ----- §10-1: 팔로우(018) — "작성자" 칸이 대상 본인 -----

    @Test
    void section10_1Follow() throws Exception {
        row("팔로우", id -> put("/api/members/mxauthor/follow").with(csrf()), () -> 0, 401, 200, 200, 400, 200);
        row("언팔로우", id -> delete("/api/members/mxauthor/follow").with(csrf()), () -> 0, 401, 200, 200, 400, 200);
        row("팔로워 목록", id -> get("/api/members/mxauthor/followers"), () -> 0, 200, 200, 200, 200, 200);
        row("팔로잉 목록", id -> get("/api/members/mxauthor/following"), () -> 0, 200, 200, 200, 200, 200);
        row("피드", id -> get("/api/feed"), () -> 0, 401, 200, 200, 200, 200);
    }

    // ----- 판정 순서(42 §3) -----

    @Test
    void decisionOrder() throws Exception {
        long othersPost = posts.draft(author, "", "", 0);
        // ② 계정 상태가 ③ 대상보다 먼저: 인증 전 회원이 남의 글 저장 → 403
        assertThat(run(put("/api/posts/" + othersPost + "/autosave").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(saveBody(0)), TestAuth.member(unverified)).getStatus()).isEqualTo(403);
        // ① 로그인이 먼저: 비회원이 없는 글 → 401
        assertThat(run(put("/api/posts/987654/autosave").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(saveBody(0)), null).getStatus()).isEqualTo(401);
        // ③ 대상이 ⑤ 값 검사보다 먼저: 모르는 공개 범위 값으로 남의 글 → 404
        long pub = posts.published(author, "글", "본문", 1, T);
        assertThat(run(patch("/api/posts/" + pub + "/visibility").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"FRIENDS\"}"), TestAuth.member(other)).getStatus()).isEqualTo(404);
        // 작성자가 휴지통 글 공개 범위 변경 → 404
        posts.trash(pub);
        assertThat(run(patch("/api/posts/" + pub + "/visibility").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"visibility\":\"PRIVATE\"}"), TestAuth.member(author)).getStatus()).isEqualTo(404);
    }

    // ----- 표 7·8: 프로필·계정·내 글 관리·사진 -----

    @Test
    void table7And8AccountAndManage() throws Exception {
        Map<String, RequestPostProcessor> self = new LinkedHashMap<>();
        self.put("비회원", null);
        self.put("인증 전", TestAuth.member(unverified));
        self.put("본인", TestAuth.member(author));
        int[][] expected = {
            {401, 200, 200}, // 닉네임·소개 수정
            {401, 200, 200}, // 기본 공개 범위
            {401, 200, 200}, // 내 글 관리 목록
            {401, 403, 200}, // 사진 업로드 승인
        };
        int i = 0;
        for (Map.Entry<String, RequestPostProcessor> actor : self.entrySet()) {
            MockHttpServletRequestBuilder[] requests = {
                patch("/api/me/profile").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"bio\":\"소개\"}"),
                patch("/api/me/settings").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"defaultVisibility\":\"PRIVATE\"}"),
                get("/api/me/posts"),
                post("/api/images/presign").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"purpose\":\"POST\",\"contentType\":\"image/webp\",\"size\":100}")
            };
            for (int r = 0; r < requests.length; r++) {
                assertThat(run(requests[r], actor.getValue()).getStatus()).as("행 %d / %s", r, actor.getKey()).isEqualTo(expected[r][i]);
            }
            i++;
        }
        // 화면 요청은 로그인 화면으로
        assertThat(run(get("/manage/posts"), null).getStatus()).isEqualTo(303);
        assertThat(run(get("/settings"), null).getStatus()).isEqualTo(303);
        // 블로그 보기: 누구나, 탈퇴 유예 주소는 404
        assertThat(run(get("/@mxother"), null).getStatus()).isEqualTo(200);
        jdbc.update("UPDATE member SET status = 'WITHDRAWN', withdrawn_at = now() WHERE id = ?", other);
        assertThat(run(get("/@mxother"), null).getStatus()).isEqualTo(404);
    }
}
