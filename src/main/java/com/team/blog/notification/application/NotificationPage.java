package com.team.blog.notification.application;

import java.util.List;

public record NotificationPage(List<NotificationItem> items, String nextCursor) {
}
