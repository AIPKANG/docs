package com.team.blog.media.web;

import com.team.blog.media.application.StorageUsageQuery;
import com.team.blog.shared.security.CurrentUserProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /api/me/storage}(008 contracts). */
@RestController
public class StorageUsageApiController {

    private final StorageUsageQuery usageQuery;
    private final CurrentUserProvider currentUserProvider;

    public StorageUsageApiController(StorageUsageQuery usageQuery, CurrentUserProvider currentUserProvider) {
        this.usageQuery = usageQuery;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping("/api/me/storage")
    public StorageUsageQuery.Usage usage() {
        return usageQuery.usage(currentUserProvider.current());
    }
}
