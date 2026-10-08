package com.team.blog.moderation.web;

import com.team.blog.moderation.application.ReportService;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** 신고 API(43 §2): 처음이면 201, 같은 대상을 다시 신고하면 200(새로 만들지 않음). 응답에 신고 수·다른 신고자는 없다. */
@RestController
public class ReportApiController {

    private final ReportService reportService;
    private final CurrentUserProvider currentUserProvider;

    public ReportApiController(ReportService reportService, CurrentUserProvider currentUserProvider) {
        this.reportService = reportService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping("/api/reports")
    public ResponseEntity<ReportService.ReportResult> report(@RequestBody(required = false) JsonNode body) {
        JsonNode b = body == null ? tools.jackson.databind.node.JsonNodeFactory.instance.objectNode() : body;
        ReportService.ReportResult result = reportService.report(currentUserProvider.current(), b.path("targetType").asString(""),
                b.path("targetId").asLong(0), b.path("reason").asString(null), b.path("detail").asString(null));
        return ResponseEntity.status(result.created() ? 201 : 200).body(result);
    }
}
