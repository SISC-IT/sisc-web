package org.sejongisc.backend.assetmanagement.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.assetmanagement.dto.AssetManagementAccountAccessResponse;
import org.sejongisc.backend.assetmanagement.model.AccountBalance;
import org.sejongisc.backend.assetmanagement.model.AccountEvaluationInfo;
import org.sejongisc.backend.assetmanagement.model.DailyBalanceInfo;
import org.sejongisc.backend.assetmanagement.service.AssetManagementAccountAuthorization;
import org.sejongisc.backend.assetmanagement.service.AssetManagementAccountService;
import org.sejongisc.backend.assetmanagement.service.AssetManagementDateProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/asset-management/accounts")
@Tag(name = "자산운용팀 계좌 조회 API", description = "키움증권 계좌 조회 전용 API")
public class AssetManagementAccountController {
  private final AssetManagementAccountService accountService;
  private final AssetManagementDateProvider dateProvider;
  private final AssetManagementAccountAuthorization authorization;

  @Operation(summary = "자산운용팀 계좌 조회 권한 확인")
  @GetMapping("/access")
  public ResponseEntity<AssetManagementAccountAccessResponse> canView(Authentication authentication) {
    return ResponseEntity.ok(new AssetManagementAccountAccessResponse(authorization.canView(authentication)));
  }

  @Operation(summary = "계좌 목록 조회", description = "계좌번호는 민감정보 노출 방지를 위해 마스킹하여 반환합니다.")
  @GetMapping
  @PreAuthorize("@assetManagementAccountAuthorization.canView(authentication)")
  public ResponseEntity<List<AccountBalance>> getAccounts() {
    return ResponseEntity.ok(accountService.getAccounts());
  }

  @Operation(summary = "일별 잔고 수익률 조회")
  @GetMapping("/daily-balance")
  @PreAuthorize("@assetManagementAccountAuthorization.canView(authentication)")
  public ResponseEntity<DailyBalanceInfo> getDailyBalance(
      @RequestParam(required = false) String date
  ) {
    String queryDate = StringUtils.hasText(date) ? date.trim() : dateProvider.currentQueryDate();
    return ResponseEntity.ok(accountService.getDailyBalance(queryDate));
  }

  @Operation(summary = "계좌 평가 현황 조회")
  @GetMapping("/evaluation")
  @PreAuthorize("@assetManagementAccountAuthorization.canView(authentication)")
  public ResponseEntity<AccountEvaluationInfo> getAccountEvaluation(
      @RequestParam(defaultValue = "KRX") String exchangeType
  ) {
    return ResponseEntity.ok(accountService.getAccountEvaluation(exchangeType));
  }
}
