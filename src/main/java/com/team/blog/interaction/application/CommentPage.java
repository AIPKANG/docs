package com.team.blog.interaction.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 댓글 한 묶음(최상위 20개 또는 답글 20개). {@code prevCursor}는 특정 댓글부터 볼 때 앞이 있으면. */
public record CommentPage(List<CommentView> items, String nextCursor,
                          @JsonInclude(JsonInclude.Include.NON_NULL) String prevCursor) {
}
