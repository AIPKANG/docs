package com.team.blog.post.web;

import com.team.blog.post.application.EditingContent;
import com.team.blog.post.application.PostDraftService;
import com.team.blog.post.application.SaveCommand;
import com.team.blog.post.application.SaveResult;
import com.team.blog.post.domain.PostContentRules;
import com.team.blog.shared.error.PostContentException;
import com.team.blog.shared.security.CurrentUserProvider;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/**
 * 편집 JSON API(contracts/web-routes.md §2). 경로에 회원 식별자가 없다 — 작성자는 로그인 정보로만 정한다(헌법 III).
 * 본문은 JSON 트리로 읽고 제목·본문·출발 버전 외의 칸(태그 등)은 무시한다(FR-003). 본문 1MB 제한은
 * {@link AutosaveBodyLimitFilter}가 먼저 건다.
 */
@RestController
@RequestMapping("/api/posts")
public class PostDraftApiController {

    private final PostDraftService draftService;
    private final CurrentUserProvider currentUserProvider;

    public PostDraftApiController(PostDraftService draftService, CurrentUserProvider currentUserProvider) {
        this.draftService = draftService;
        this.currentUserProvider = currentUserProvider;
    }

    /** 새 임시글(비교 창의 "새 임시글로 따로 저장"도 여기). */
    public record Created(long postId, long version) {
    }

    @PostMapping
    public ResponseEntity<Created> create(@RequestBody(required = false) JsonNode body) {
        String title = text(body, "title");
        String content = text(body, "contentMd");
        long id = draftService.create(currentUserProvider.current(), title, content);
        return ResponseEntity.status(HttpStatus.CREATED).body(new Created(id, 0));
    }

    /** 편집 화면 내용(제목·본문·버전·저장 시각). */
    public record EditingView(long postId, String status, String title, String contentMd, long version, Instant savedAt,
                              boolean editing) {

        static EditingView of(EditingContent c) {
            return new EditingView(c.postId(), c.status().name(), c.title(), c.contentMd(), c.version(), c.savedAt(),
                    c.editing());
        }
    }

    @GetMapping("/{postId}/editing")
    public EditingView editing(@PathVariable long postId) {
        return EditingView.of(draftService.editing(currentUserProvider.current(), postId));
    }

    @PutMapping("/{postId}/autosave")
    public SaveResult autosave(@PathVariable long postId, @RequestBody(required = false) JsonNode body) {
        return draftService.autosave(currentUserProvider.current(), postId, command(body));
    }

    @PutMapping("/{postId}/draft")
    public SaveResult save(@PathVariable long postId, @RequestBody(required = false) JsonNode body) {
        return draftService.save(currentUserProvider.current(), postId, command(body));
    }

    @DeleteMapping("/{postId}/working-copy")
    public ResponseEntity<Void> discard(@PathVariable long postId) {
        draftService.discardWorkingCopy(currentUserProvider.current(), postId);
        return ResponseEntity.noContent().build();
    }

    static SaveCommand command(JsonNode body) {
        if (body == null || !body.isObject()) {
            throw new PostContentException(PostContentRules.INVALID_REQUEST);
        }
        JsonNode base = body.get("baseVersion");
        Long baseVersion = base != null && base.isIntegralNumber() && base.canConvertToLong() ? base.longValue() : null;
        return new SaveCommand(text(body, "title"), text(body, "contentMd"), baseVersion);
    }

    private static String text(JsonNode body, String field) {
        if (body == null || !body.isObject()) {
            return null;
        }
        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw new PostContentException(PostContentRules.INVALID_REQUEST);
        }
        return node.stringValue();
    }
}
