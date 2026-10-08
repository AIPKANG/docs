package com.team.blog.account.application;

/**
 * 30일 뒤 익명 처리의 한 단계(023 FR-028·FR-029). 각 기능이 자기 데이터 단계를 Bean으로 제공하고, {@link #order()} 순서로
 * 한 회원의 한 트랜잭션 안에서 실행된다(하나라도 실패하면 그 회원 전체 취소). 새 단계는 순서 값 사이에 끼워 넣는다.
 * 순서: 10 글, 20 남의 글 댓글, 30 좋아요, 40 사진, 50 로그인 수단, 60 팔로우, 70 알림, 80 신고, 90 회원 정보(마지막).
 */
public interface WithdrawalPurgeStep {

    int order();

    void purge(long memberId);
}
