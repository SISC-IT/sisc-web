package org.sejongisc.backend.attendance.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.attendance.dto.*;
import org.sejongisc.backend.attendance.service.AttendanceAuthorizationService;
import org.sejongisc.backend.attendance.service.AttendanceSessionService;
import org.sejongisc.backend.attendance.service.SessionUserService;
import org.sejongisc.backend.common.auth.springsecurity.CustomUserDetails;
import org.sejongisc.backend.user.service.UserService;
import org.sejongisc.backend.user.service.projection.UserIdNameProjection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/attendance/sessions")
@Slf4j
@Tag(
    name = "출석 세션(Attendance Session) API",
    description = "출석 세션 생성, 조회, 수정, 삭제 및 상태 관리 관련 API"
)
public class AttendanceSessionController {

    private final AttendanceSessionService attendanceSessionService;
    private final SessionUserService sessionUserService;
    private final UserService userService;
    /**
     * 출석 세션 생성
     */
    @Operation(
            summary = "출석 세션 생성",
            description = """
                
                ## 인증(JWT): **필요**
                
                
                ## 요청 파라미터 ( `AttendanceSessionDto` )
                - **`title`**: 세션 제목
                - **`description`**: 세션 설명
                - **`allowedMinutes`**: 체크인 허용 시간 (분)
                - **`rewardPoints`**: 세션 참여 시 부여할 포인트
                
                ## 반환값 없음
                """
    )
    @PostMapping
    public ResponseEntity<Void> createSession(@AuthenticationPrincipal(expression = "userId") UUID userId,
        @RequestBody AttendanceSessionRequest request) {
        log.info("출석 세션 생성 요청: 제목={}", request.title());

        attendanceSessionService.createSession(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /**
     * 세션 상세 조회
     * - 세션 ID로 상세 정보 조회
     */
    @Operation(
            summary = "세션 상세 조회",
            description = """
                ## 인증(JWT): **필요없음**
                
                ## 요청 파라미터 ( `sessionId` )
                
                ## 반환값 (`AttendanceSessionDto`)
                - **`title`**: 세션 제목
                - **`description`**: 세션 설명
                - **`allowedMinutes`**: 체크인 허용 시간 (분)
                - **`rewardPoints`**: 세션 참여 시 부여할 포인트
                - **`status`**: 세션 상태 (OPEN, CLOSED 등)
                """
    )
    @GetMapping("/{sessionId}")
    public ResponseEntity<AttendanceSessionResponse> getSession(
        @PathVariable UUID sessionId,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UUID userId = (userDetails != null) ? userDetails.getUserId() : null;
        AttendanceSessionResponse response = attendanceSessionService.getSessionById(sessionId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * 모든 세션 목록 조회
     * - 최신 순으로 정렬
     * - 공개/비공개 세션 모두 포함
     */
    @Operation(
            summary = "모든 세션 목록 조회",
            description = """
                ## 인증(JWT): **필요없음**
                
                ## 요청 파라미터 : **없음**
                
                ## 반환값 (`List<AttendanceSessionDto>`)
                - **`title`**: 세션 제목
                - **`description`**: 세션 설명
                - **`allowedMinutes`**: 체크인 허용 시간 (분)
                - **`rewardPoints`**: 세션 참여 시 부여할 포인트
                - **`status`**: 세션 상태 (OPEN, CLOSED 등)
                
                ## 설명
                - 세션 관리자 권한 여부에 따라 반환되는 세션 정보가 다를 수 있음
                """
    )
    @GetMapping
    public ResponseEntity<List<AttendanceSessionResponse>> getAllSessions() {

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getAllSessions();


        return ResponseEntity.ok(sessions);
    }


    /**
     * 현재 활성 세션 목록 조회
     * - 체크인 가능한 세션들만 조회
     */
    @Operation(
            summary = "활성 세션 목록 조회",
            description = """
                ## 인증(JWT): **필요없음**
                
                ## 요청 파라미터 : **없음**
                
                ## 반환값 (`List<AttendanceSessionDto>`)
                - **`title`**: 세션 제목
                - **`description`**: 세션 설명
                - **`allowedMinutes`**: 체크인 허용 시간 (분)
                - **`rewardPoints`**: 세션 참여 시 부여할 포인트
                - **`status`**: 세션 상태 (OPEN, CLOSED 등)
                """
    )
    @GetMapping("/active")
    public ResponseEntity<List<AttendanceSessionResponse>> getActiveSessions() {
        log.info("활성 출석 세션 조회");

        List<AttendanceSessionResponse> sessions = attendanceSessionService.getActiveSessions();

        return ResponseEntity.ok(sessions);
    }

    /**
     * 세션 정보 수정 (관리자용)
     * - 제목, 시간, 위치, 반경 등 수정 가능
     * - 코드는 변경 불가
     */
    @Operation(
            summary = "세션 정보 수정",
            description = """
                
                """
    )
    @PutMapping("/{sessionId}")
    public ResponseEntity<Void> updateSession(
            @PathVariable UUID sessionId,
            @RequestBody AttendanceSessionRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails){

        log.info("출석 세션 수정: 세션ID={}", sessionId);
        attendanceSessionService.updateSession(sessionId, request,userDetails.getUserId());

        log.info("출석 세션 수정 완료: 세션ID={}", sessionId);

        return ResponseEntity.ok().build();
    }


    /**
     * 세션 종료 (관리자용)
     * - 세션 상태를 CLOSED로 변경
     * - 체크인 수동 종료
     */
    @Operation(
            summary = "세션 종료",
            description = """
                ## 인증(JWT): **필요**
                
                """
    )
    @PostMapping("/{sessionId}/close")
    public ResponseEntity<Void> closeSession(@PathVariable UUID sessionId,@AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("출석 세션 종료: 세션ID={}", sessionId);


        attendanceSessionService.closeSession(sessionId,userDetails.getUserId());

        log.info("출석 세션 종료 완료: 세션ID={}", sessionId);

        return ResponseEntity.ok().build();
    }


    /**
     * 세션 삭제 (관리자용)
     * - 세션 완전 삭제 (출석 기록도 함께 삭제)
     * - 주의: 복구 불가
     */
    @Operation(
            summary = "세션 삭제",
            description = """
                ## 인증(JWT): **필요**

                """
    )
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId,
    @AuthenticationPrincipal CustomUserDetails userDetails) {
        log.info("출석 세션 삭제: 세션ID={}", sessionId);

        attendanceSessionService.deleteSession(sessionId,userDetails.getUserId());

        log.info("출석 세션 삭제 완료: 세션ID={}", sessionId);

        return ResponseEntity.noContent().build();
    }


    //    /**
//     * 세션 위치 재설정 (관리자용)
//     * - 기존 위치 정보를 새로운 위치로 업데이트
//     * - 반경은 기존 값 유지
//     */
//    @Operation(
//            summary = "세션 위치 재설정",
//            description = "세션의 위치 정보를 재설정합니다. (관리자 전용) " +
//                    "새로운 위도와 경도로 출석 기반 위치 검증 범위를 변경할 수 있습니다."
//    )
//    @PutMapping("/{sessionId}/location")
//    @PreAuthorize("hasRole('PRESIDENT') or hasRole('VICE_PRESIDENT')")
//    public ResponseEntity<AttendanceSessionResponse> updateSessionLocation(
//            @PathVariable UUID sessionId,
//            @Valid @RequestBody SessionLocationUpdateRequest request) {
//        log.info("세션 위치 재설정: 세션ID={}, 위도={}, 경도={}",
//                sessionId, request.getLatitude(), request.getLongitude());
//
//        AttendanceSessionResponse response = attendanceSessionService.updateSessionLocation(sessionId, request);
//
//        log.info("세션 위치 재설정 완료: 세션ID={}", sessionId);
//
//        return ResponseEntity.ok(response);
//    }

}
