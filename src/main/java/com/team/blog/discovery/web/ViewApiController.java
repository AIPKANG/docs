package com.team.blog.discovery.web;

import com.team.blog.discovery.application.ViewRecorder;
import com.team.blog.shared.security.CurrentUserProvider;
import com.team.blog.shared.web.ClientIpResolver;
import com.team.blog.shared.web.VisitorCookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** 조회 기록 {@code POST /api/posts/{id}/views}(31 §4-2): 셌든 안 셌든 204, 볼 수 없으면 404, 1분 60번 초과 429. */
@RestController
public class ViewApiController {

    private final ViewRecorder recorder;
    private final CurrentUserProvider currentUserProvider;
    private final ClientIpResolver clientIpResolver;

    public ViewApiController(ViewRecorder recorder, CurrentUserProvider currentUserProvider, ClientIpResolver clientIpResolver) {
        this.recorder = recorder;
        this.currentUserProvider = currentUserProvider;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/api/posts/{postId}/views")
    public ResponseEntity<Void> record(@PathVariable long postId, HttpServletRequest request) {
        boolean prefetch = "prefetch".equalsIgnoreCase(request.getHeader("Purpose"))
                || "prefetch".equalsIgnoreCase(request.getHeader("Sec-Purpose"));
        recorder.record(postId, new ViewRecorder.Visit(currentUserProvider.current(), VisitorCookie.read(request),
                clientIpResolver.resolve(request), request.getHeader("User-Agent"), prefetch));
        return ResponseEntity.noContent().build();
    }
}
