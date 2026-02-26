package org.sejongisc.backend.activity.entity;

public enum ActivityType {
    ATTENDANCE,    // 출석체크
    BOARD_POST,    // 게시글 작성
    BOARD_COMMENT, // 댓글 작성
    BOARD_LIKE,    // 좋아요
    BETTING_JOIN,  // 베팅 참여
    AUTH_LOGIN     // 로그인 (방문자 통계용)
}