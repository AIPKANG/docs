package com.team.blog.post.web;

import com.team.blog.post.application.EditingContent;
import com.team.blog.post.application.PostDraftService;
import com.team.blog.post.application.PostProperties;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.web.Redirects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import tools.jackson.databind.json.JsonMapper;

/**
 * 편집 화면(contracts/web-routes.md §1). [새 글]은 POST로만 행을 만든다(GET에 부작용 없음, research R-11).
 * 서버 현재 내용과 저장 간격은 {@code data-state} 속성 하나에 JSON으로 넘긴다 — 속성 값은 Thymeleaf가 이스케이프한다.
 */
@Controller
public class EditorController {

    private final PostDraftService draftService;
    private final CurrentUserProvider currentUserProvider;
    private final PostProperties properties;
    private final JsonMapper jsonMapper;

    public EditorController(PostDraftService draftService, CurrentUserProvider currentUserProvider,
                            PostProperties properties, JsonMapper jsonMapper) {
        this.draftService = draftService;
        this.currentUserProvider = currentUserProvider;
        this.properties = properties;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/write")
    public RedirectView create() {
        long postId = draftService.create(currentUserProvider.current(), "", "");
        return Redirects.seeOther("/write/" + postId);
    }

    @GetMapping("/write/{postId}")
    public String editor(@PathVariable long postId, Model model) {
        Optional<CurrentUser> user = currentUserProvider.current();
        EditingContent content = draftService.editing(user, postId);
        model.addAttribute("post", content);
        model.addAttribute("titleMaxLength", properties.titleMaxLength());
        model.addAttribute("published", content.status().name().equals("PUBLISHED"));
        PostDraftService.PublishDefaults defaults = draftService.publishDefaults(user, postId);
        Map<String, Object> state = state(user.orElseThrow().memberId(), content);
        state.put("visibility", defaults.visibility());
        state.put("tags", defaults.tags());
        state.put("maxTags", properties.maxTags());
        model.addAttribute("visibility", defaults.visibility());
        model.addAttribute("state", jsonMapper.writeValueAsString(state));
        return "post/editor";
    }

    private Map<String, Object> state(long memberId, EditingContent content) {
        PostProperties.Editor editor = properties.editor();
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("memberId", memberId);
        state.put("postId", content.postId());
        state.put("status", content.status().name());
        state.put("title", content.title());
        state.put("contentMd", content.contentMd());
        state.put("version", content.version());
        state.put("savedAt", content.savedAt() == null ? null : content.savedAt().toString());
        state.put("editing", content.editing());
        state.put("localSaveDelayMs", editor.localSaveDelay().toMillis());
        state.put("serverSaveDelayMs", editor.serverSaveDelay().toMillis());
        state.put("serverSaveMaxIntervalMs", editor.serverSaveMaxInterval().toMillis());
        state.put("retryMaxMs", editor.retryMax().toMillis());
        state.put("backupTtlMs", editor.backupTtl().toMillis());
        state.put("titleMaxLength", properties.titleMaxLength());
        return state;
    }
}
