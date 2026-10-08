package com.team.blog.notification.web;

import com.team.blog.notification.application.NotificationService;
import com.team.blog.notification.application.NotificationType;
import com.team.blog.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.view.RedirectView;

/** 전체 알림 페이지(25 §3)와 스크립트 없이도 되는 폼(누르면 읽음 후 이동, 모두 읽음, 삭제, 설정 저장). */
@Controller
public class NotificationPageController {

    private final NotificationService service;
    private final CurrentUserProvider currentUserProvider;

    public NotificationPageController(NotificationService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/notifications")
    public String page(@RequestParam(required = false) String cursor, Model model, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        model.addAttribute("page", service.page(currentUserProvider.current(), cursor, null));
        return "notification/list";
    }

    @PostMapping("/notifications/{id}/open")
    public RedirectView open(@PathVariable long id) {
        String url = service.read(currentUserProvider.current(), id).orElse("/notifications");
        return seeOther(url);
    }

    @PostMapping("/notifications/read-all")
    public RedirectView readAll() {
        service.readAll(currentUserProvider.current());
        return seeOther("/notifications");
    }

    @PostMapping("/notifications/{id}/delete")
    public RedirectView delete(@PathVariable long id) {
        service.delete(currentUserProvider.current(), id);
        return seeOther("/notifications");
    }

    /** 설정 폼: 체크된 종류만 켜짐. */
    @PostMapping("/settings/notifications")
    public RedirectView settings(@RequestParam Map<String, String> form) {
        Map<String, Boolean> changes = new HashMap<>();
        NotificationType.MUTABLE.forEach(t -> changes.put(t.name(), form.containsKey(t.name())));
        service.updateSettings(currentUserProvider.current(), changes);
        return seeOther("/settings#notification-heading");
    }

    private static RedirectView seeOther(String url) {
        RedirectView view = new RedirectView(url);
        view.setStatusCode(HttpStatus.SEE_OTHER);
        view.setExposeModelAttributes(false);
        return view;
    }

}
