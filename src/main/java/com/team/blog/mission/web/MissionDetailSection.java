package com.team.blog.mission.web;

import com.team.blog.mission.application.MissionService;
import com.team.blog.post.application.PostDetail;
import com.team.blog.post.web.PostDetailSection;
import com.team.blog.shared.security.CurrentUser;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/** 글 화면(034): 참여한 미션 배지(누구나), 공개 글의 글쓴이에게는 진행 중인 미션에 참여하기. */
@Component
public class MissionDetailSection implements PostDetailSection {

    private final MissionService missions;

    public MissionDetailSection(MissionService missions) {
        this.missions = missions;
    }

    @Override
    public void contribute(Model model, PostDetail post, Optional<CurrentUser> viewer, Map<String, String> params) {
        if (!missions.enabled()) {
            return;
        }
        model.addAttribute("postMissions", missions.missionsOfPost(post.id()));
        boolean author = viewer.map(v -> v.memberId() == post.authorId()).orElse(false);
        if (author && post.isPublic() && !post.hidden()) {
            model.addAttribute("joinableMissions", missions.list(true, 20));
        }
    }
}
