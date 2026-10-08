/**
 * 운영 모듈(022, 43): 신고 접수, 관리자의 숨김·반려·숨김 해제, 회원 정지. 글·댓글 내용은 바꾸지 않고 숨김 표시만 한다.
 * 글 읽기 판정은 post의 {@code PostReadAccess}, 댓글 수는 {@code PostCounters}, 세션은 account의 {@code SessionRevoker}로.
 */
package com.team.blog.moderation;
