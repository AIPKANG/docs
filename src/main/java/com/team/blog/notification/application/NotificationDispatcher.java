package com.team.blog.notification.application;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 알림 처리를 원래 요청과 떼어 놓는다(FR-002, 20 §2-2). 스레드 하나·크기 제한 대기열이라 같은 글의 좋아요·취소 순서가 지켜지고,
 * 가득 차면 버리고 경고한다. 처리 중 예외는 기록만 한다(원래 요청은 이미 커밋됨).
 */
@Component
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private final NotificationProperties properties;
    private final ThreadPoolExecutor executor;

    public NotificationDispatcher(NotificationProperties properties) {
        this.properties = properties;
        this.executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS, new ArrayBlockingQueue<>(properties.queueCapacity()),
                r -> {
                    Thread t = new Thread(r, "notification");
                    t.setDaemon(true);
                    return t;
                });
    }

    public void submit(String what, Runnable work) {
        Runnable safe = () -> {
            try {
                work.run();
            } catch (RuntimeException e) {
                log.warn("알림 처리 실패: {} cause={}", what, e.getClass().getSimpleName());
            }
        };
        if (!properties.async()) {
            safe.run();
            return;
        }
        try {
            executor.execute(safe);
        } catch (RejectedExecutionException e) {
            log.warn("알림 대기열이 가득 차 버림: {}", what);
        }
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
