package org.sejongisc.backend.attendance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false)
    private String title;       // "세투연 9/17"

    private String tag;         // "금융IT 출석", "동아리 전체"

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;     // 세션 시작 시간

    @Column(name = "window_seconds")
    private Integer windowSeconds;      // 체크인 가능 시간(초) - 1800 = 30분

    @Column(unique = true, length = 6)
    private String code;            // 6자리 출석 코드 "942715"

    @Column(name = "reward_points")
    private Integer rewardPoints;       // 출석 시 지급할 포인트

    @Embedded
    private Location location;      // 위치 기반 출석을 위한 GPS 좌표


}
