package com.team.blog.note.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** 짧은 기록(033). */
@ConfigurationProperties("blog.notes")
public record NoteProperties(@DefaultValue("true") boolean enabled, @DefaultValue("280") int maxLength,
                             @DefaultValue("20") int perHour, @DefaultValue("30") int pageSize) {
}
