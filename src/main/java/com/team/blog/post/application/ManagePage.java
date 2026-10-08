package com.team.blog.post.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import java.util.Map;

/** 내 글 관리 한 묶음(20개). {@code counts}는 첫 요청에만(탭별 글 수). */
public record ManagePage(List<ManageRow> items, String nextCursor,
                         @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Long> counts) {
}
