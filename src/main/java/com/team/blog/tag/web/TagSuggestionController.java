package com.team.blog.tag.web;

import com.team.blog.account.application.AiConsentService;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUser;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.tag.application.suggest.AiTagProperties;
import com.team.blog.tag.application.suggest.TagSuggestionService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** AI 태그 추천 API(34 §9)와 AI 전송 동의(§7). */
@RestController
public class TagSuggestionController {

    private final TagSuggestionService service;
    private final AiConsentService consentService;
    private final AiTagProperties properties;
    private final AccountGuard accountGuard;
    private final CurrentUserProvider currentUserProvider;

    public TagSuggestionController(TagSuggestionService service, AiConsentService consentService, AiTagProperties properties,
                                   AccountGuard accountGuard, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.consentService = consentService;
        this.properties = properties;
        this.accountGuard = accountGuard;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/posts/{postId}/tag-suggestions")
    public TagSuggestionService.Result suggest(@PathVariable long postId, @RequestBody(required = false) JsonNode body) {
        JsonNode b = body == null ? tools.jackson.databind.node.JsonNodeFactory.instance.objectNode() : body;
        List<String> tags = new java.util.ArrayList<>();
        if (b.path("currentTags").isArray()) {
            b.path("currentTags").forEach(t -> tags.add(t.asString("")));
        }
        return service.suggest(currentUserProvider.current(), postId, new TagSuggestionService.Request(
                b.path("title").asString(""), b.path("contentMd").asString(""), tags, b.path("visibility").asString("PUBLIC"),
                b.path("refresh").asBoolean(false)));
    }

    /** 편집 화면이 버튼·동의 창을 그릴 때 쓰는 상태. */
    @GetMapping("/api/me/ai-consent")
    public Map<String, Object> consent() {
        CurrentUser user = accountGuard.requireLoggedIn(currentUserProvider.current());
        return Map.of("enabled", properties.enabled(), "consented", consentService.hasConsent(user.memberId()));
    }

    @PostMapping("/api/me/ai-consent")
    public ResponseEntity<Void> agree() {
        consentService.agree(currentUserProvider.current());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/me/ai-consent")
    public ResponseEntity<Void> withdraw() {
        consentService.withdraw(currentUserProvider.current());
        return ResponseEntity.noContent().build();
    }
}
