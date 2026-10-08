package com.team.blog.shared.event;

import java.util.List;

/**
 * 한 대상의 대기 신고가 한 번에 처리됨(022 FR-029). 알림이 신고마다(=신고자마다) 처리 결과를 보낸다. 대상 내용·작성자는 담지 않는다.
 *
 * @param result {@code ACTION_TAKEN} 또는 {@code NO_VIOLATION}
 */
public record ReportsResolved(List<Report> reports, String result) implements DomainEvent {

    public record Report(long reportId, long reporterId) {
    }
}
