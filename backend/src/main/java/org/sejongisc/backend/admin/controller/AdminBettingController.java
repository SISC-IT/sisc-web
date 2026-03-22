package org.sejongisc.backend.admin.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.betting.entity.Scope;
import org.sejongisc.backend.betting.service.BettingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/bet-rounds")
@Tag(
        name = "00. 관리자 모의 트레이딩 관리 API",
        description = "모의 트레이딩 관리 관련 API 제공"
)
public class AdminBettingController {

    private final BettingService bettingService;

    @Operation(
        summary = "일일 베팅 라운드 수동 오픈",
        description = """
            ## 인증/권한
            - 관리자 전용 API

            ## 요청 바디
            - 없음

            ## 동작 설명
            - `Scope.DAILY` 라운드를 새로 생성하고 즉시 오픈 상태로 저장합니다.
            - 시세 데이터에서 임의의 종목 1개를 골라 해당 종목으로 일일 라운드를 만듭니다.
            - 생성된 라운드의 주요 값:
              - `openAt`: 호출한 날짜의 오전 9시
              - `lockAt`: 호출한 날짜의 오후 10시
              - `allowFree`: 20% 확률로 무료 베팅 허용
            - 성공 시 문자열 `"DAILY 라운드 생성"`을 반환합니다.

            ## 프론트 참고
            - 버튼 클릭형 수동 개시 API로 보면 됩니다.
            - 중복 생성 방지 로직이 없으므로, 같은 날 여러 번 호출하면 일일 라운드가 여러 개 생길 수 있습니다.
            - 시세 데이터가 없으면 실패할 수 있습니다.
            """
    )
    @PostMapping("/daily/open")
    @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
    public ResponseEntity<String> createDailyRound() {
        bettingService.createBetRound(Scope.DAILY);
        return ResponseEntity.ok("DAILY 라운드 생성");
    }

    @Operation(
        summary = "주간 베팅 라운드 수동 오픈",
        description = """
            ## 인증/권한
            - 관리자 전용 API

            ## 요청 바디
            - 없음

            ## 동작 설명
            - `Scope.WEEKLY` 라운드를 새로 생성하고 즉시 오픈 상태로 저장합니다.
            - 시세 데이터에서 임의의 종목 1개를 골라 해당 종목으로 주간 라운드를 만듭니다.
            - 생성된 라운드의 주요 값:
              - `openAt`: 호출 시점이 속한 주의 월요일 오전 9시
              - `lockAt`: 호출 시점이 속한 주의 금요일 오후 10시
              - `allowFree`: 20% 확률로 무료 베팅 허용
            - 성공 시 문자열 `"WEEKLY 라운드 생성"`을 반환합니다.

            ## 프론트 참고
            - 운영자가 주간 라운드를 수동으로 열 때 호출합니다.
            - 중복 생성 방지 로직이 없으므로, 같은 주에 여러 번 호출하면 주간 라운드가 여러 개 생길 수 있습니다.
            - 시세 데이터가 없으면 실패할 수 있습니다.
            """
    )
    @PostMapping("/weekly/open")
    @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
    public ResponseEntity<String> createWeeklyRound() {
        bettingService.createBetRound(Scope.WEEKLY);
        return ResponseEntity.ok("WEEKLY 라운드 생성");
    }

    @Operation(
        summary = "마감 시각이 지난 라운드 일괄 종료",
        description = """
            ## 인증/권한
            - 관리자 전용 API

            ## 요청 바디
            - 없음

            ## 동작 설명
            - 현재 시각 기준으로 `status = true` 이고 `lockAt <= now` 인 라운드를 찾아 일괄 종료합니다.
            - 종료는 "유저가 더 이상 베팅할 수 없게 상태를 닫는 작업"이며, 정산은 아직 하지 않습니다.
            - 성공 시 문자열 `"라운드 종료"`를 반환합니다.

            ## 프론트 참고
            - 배팅 접수만 마감하는 API입니다. 포인트 정산은 별도의 `/settle` API를 호출해야 반영됩니다.
            - 종료 대상이 없어도 에러 없이 성공 응답이 내려오며, 이 경우 실질 변경은 없습니다.
            """
    )
    @PostMapping("/close")
    @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
    public ResponseEntity<String> closeRounds() {
        bettingService.closeBetRound();
        return ResponseEntity.ok("라운드 종료");
    }

    @Operation(
        summary = "종료된 라운드 정산 실행",
        description = """
            ## 인증/권한
            - 관리자 전용 API

            ## 요청 바디
            - 없음

            ## 동작 설명
            - 현재 시각 기준으로 이미 닫혀 있고(`status = false`), 아직 정산되지 않았으며(`settleAt = null`), `lockAt <= now` 인 라운드를 정산합니다.
            - 최신 시세 데이터를 조회해 상승/하락/보합을 판정하고, 각 사용자 베팅 결과를 `WIN`/`LOSE`/`DRAW`로 처리합니다.
            - 무료 베팅 보상, 유료 베팅 보상, 보합 환불, 잔여 포인트 정리까지 함께 수행합니다.
            - 성공 시 문자열 `"정산"`을 반환합니다.

            ## 프론트 참고
            - 이 API를 호출해야 사용자 포인트와 베팅 결과가 실제 반영됩니다.
            - 정산 대상이 없어도 에러 없이 성공 응답이 내려올 수 있습니다.
            - 특정 라운드의 최신 시세 데이터가 없으면 그 라운드는 건너뛰고, 나머지 라운드만 계속 정산합니다.
            """
    )
    @PostMapping("/settle")
    @PreAuthorize("hasAnyRole('VICE_PRESIDENT', 'PRESIDENT', 'SYSTEM_ADMIN')")
    public ResponseEntity<String> settleRounds() {
        bettingService.settleUserBets();
        return ResponseEntity.ok("정산");
    }
}
