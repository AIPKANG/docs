package com.team.blog.mission.web;

import com.team.blog.mission.application.MissionService;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** 머리말 [미션] 메뉴를 켤지(034). */
@ControllerAdvice
public class MissionLayoutAdvice {

    private final MissionService missions;

    public MissionLayoutAdvice(MissionService missions) {
        this.missions = missions;
    }

    @ModelAttribute("missionsEnabled")
    public boolean missionsEnabled() {
        return missions.enabled();
    }
}
