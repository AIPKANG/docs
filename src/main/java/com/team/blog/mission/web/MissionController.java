package com.team.blog.mission.web;

import static com.team.blog.shared.web.Redirects.seeOther;

import com.team.blog.mission.application.MissionService;
import com.team.blog.post.application.CardDates;
import com.team.blog.shared.security.AccountGuard;
import com.team.blog.shared.security.CurrentUserProvider;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/** 같은 주제로 쓰기(034, 강성찬 개인 확장): 미션 목록·만들기·상세(참여 글 릴레이)·참여·빠지기. 스크립트 없이 폼으로. */
@Controller
@ConditionalOnProperty(name = "blog.missions.enabled", havingValue = "true", matchIfMissing = true)
public class MissionController {

    private final MissionService missions;
    private final AccountGuard accountGuard;
    private final CurrentUserProvider currentUserProvider;
    private final Clock clock;

    public MissionController(MissionService missions, AccountGuard accountGuard, CurrentUserProvider currentUserProvider,
                             Clock clock) {
        this.missions = missions;
        this.accountGuard = accountGuard;
        this.currentUserProvider = currentUserProvider;
        this.clock = clock;
    }

    @GetMapping("/missions")
    public String list(Model model) {
        model.addAttribute("active", missions.list(true, 50));
        model.addAttribute("ended", missions.list(false, 30));
        model.addAttribute("now", clock.instant());
        return "mission/list";
    }

    @GetMapping("/missions/new")
    public String form() {
        accountGuard.requireWritable(currentUserProvider.current());
        return "mission/new";
    }

    @PostMapping("/missions")
    public RedirectView create(@RequestParam("title") String title,
                               @RequestParam(value = "description", required = false) String description,
                               @RequestParam(value = "days", defaultValue = "7") int days) {
        long id = missions.create(currentUserProvider.current(), title, description, days);
        return seeOther("/missions/" + id);
    }

    @GetMapping("/missions/{id}")
    public String detail(@PathVariable("id") long id, Model model) {
        MissionService.Mission mission = missions.get(id);
        model.addAttribute("mission", mission);
        model.addAttribute("entries", missions.entries(id));
        long seconds = Duration.between(clock.instant(), mission.endsAt()).getSeconds();
        long left = (seconds + 86_399) / 86_400; // 남은 날은 올림(6일 23시간 → 7일 남음)
        model.addAttribute("leftLabel", mission.active() ? (seconds >= 86_400 ? left + "일 남음" : "오늘 마감") : "끝난 미션");
        model.addAttribute("startedLabel", CardDates.label(mission.startsAt(), clock.instant()));
        return "mission/detail";
    }

    @PostMapping("/missions/{id}/join")
    public RedirectView join(@PathVariable("id") long id, @RequestParam("postId") long postId) {
        missions.join(currentUserProvider.current(), id, postId);
        return seeOther("/missions/" + id);
    }

    /** 글 화면의 참여 폼(미션을 고르는 select). */
    @PostMapping("/missions/join")
    public RedirectView joinFromPost(@RequestParam("missionId") long missionId, @RequestParam("postId") long postId) {
        missions.join(currentUserProvider.current(), missionId, postId);
        return seeOther("/missions/" + missionId);
    }

    @PostMapping("/missions/{id}/leave")
    public RedirectView leave(@PathVariable("id") long id) {
        missions.leave(currentUserProvider.current(), id);
        return seeOther("/missions/" + id);
    }
}
