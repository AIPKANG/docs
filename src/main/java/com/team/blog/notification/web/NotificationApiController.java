package com.team.blog.notification.web;

import com.team.blog.notification.application.NotificationPage;
import com.team.blog.notification.application.NotificationService;
import com.team.blog.shared.security.CurrentUserProvider;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알림 API(25 §5·§7). 모두 본인 것만, 주소에 회원 번호 없음. */
@RestController
public class NotificationApiController {

    private final NotificationService service;
    private final CurrentUserProvider currentUserProvider;

    public NotificationApiController(NotificationService service, CurrentUserProvider currentUserProvider) {
        this.service = service;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/notifications/unread-count")
    public Map<String, Long> unreadCount(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        return Map.of("count", service.unreadCount(currentUserProvider.current()));
    }

    @GetMapping("/api/notifications")
    public NotificationPage list(@RequestParam(required = false) String cursor, @RequestParam(required = false) Integer size,
                                 HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        return service.page(currentUserProvider.current(), cursor, size);
    }

    @PatchMapping("/api/notifications/{id}/read")
    public ResponseEntity<Void> read(@PathVariable long id) {
        service.read(currentUserProvider.current(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/api/notifications/read-all")
    public Map<String, Integer> readAll() {
        return Map.of("updated", service.readAll(currentUserProvider.current()));
    }

    @DeleteMapping("/api/notifications/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(currentUserProvider.current(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/me/notification-settings")
    public Map<String, Boolean> settings() {
        return service.settings(currentUserProvider.current());
    }

    @PutMapping("/api/me/notification-settings")
    public Map<String, Boolean> updateSettings(@RequestBody Map<String, Boolean> body) {
        return service.updateSettings(currentUserProvider.current(), body);
    }
}
