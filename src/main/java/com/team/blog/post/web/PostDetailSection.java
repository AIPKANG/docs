package com.team.blog.post.web;

import com.team.blog.post.application.PostDetail;
import com.team.blog.shared.security.CurrentUser;
import java.util.Map;
import java.util.Optional;
import org.springframework.ui.Model;

/**
 * 글 상세 화면에 다른 모듈이 영역을 더하는 확장점(헌법 I: post가 다른 모듈을 직접 부르지 않음). 댓글(014)·좋아요(015) 등이
 * Bean으로 구현해 모델에 값을 넣고, 템플릿은 그 값이 있을 때 조각을 그린다.
 */
public interface PostDetailSection {

    void contribute(Model model, PostDetail post, Optional<CurrentUser> viewer, Map<String, String> params);
}
