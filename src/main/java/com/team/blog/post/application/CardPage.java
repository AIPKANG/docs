package com.team.blog.post.application;

import java.util.List;

/** 카드 9개와 다음 이어 보기 기준(없으면 null → [더 보기] 숨김). */
public record CardPage(List<PostCard> items, String nextCursor) {
}
