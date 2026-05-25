package org.sejongisc.backend.assetmanagement.infrastructure.kiwoom;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.AccountEvaluationRequest;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.AccountEvaluationResponse;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.DailyBalanceRequest;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.DailyBalanceResponse;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.KiwoomAccountResponse;
import org.sejongisc.backend.assetmanagement.infrastructure.kiwoom.dto.KiwoomResponse;
import org.sejongisc.backend.assetmanagement.model.AccountBalance;
import org.sejongisc.backend.assetmanagement.model.AccountEvaluationInfo;
import org.sejongisc.backend.assetmanagement.model.DailyBalanceInfo;
import org.sejongisc.backend.assetmanagement.model.DomesticExchangeType;
import org.sejongisc.backend.assetmanagement.service.AssetManagementAccountClient;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Slf4j
@Component
public class KiwoomAccountClient implements AssetManagementAccountClient {
  private static final String ACCOUNT_ENDPOINT = "/api/dostk/acnt";

  private final WebClient kiwoomWebClient;
  private final KiwoomTokenManager tokenManager;
  private final KiwoomRateLimiter rateLimiter;
  private final int maxPages;
  private final Duration blockTimeout;

  public KiwoomAccountClient(
      @Qualifier("kiwoomWebClient") WebClient kiwoomWebClient,
      KiwoomTokenManager tokenManager,
      KiwoomRateLimiter rateLimiter,
      @Value("${kiwoom.api.pagination.max-pages:20}") int maxPages,
      @Value("${kiwoom.api.timeout.block-ms:7000}") long blockTimeoutMillis
  ) {
    this.kiwoomWebClient = kiwoomWebClient;
    this.tokenManager = tokenManager;
    this.rateLimiter = rateLimiter;
    this.maxPages = Math.max(1, maxPages);
    this.blockTimeout = Duration.ofMillis(Math.max(1000, blockTimeoutMillis));
  }

  @Override
  public List<AccountBalance> fetchAccounts() {
    List<KiwoomAccountResponse> pages =
        executePagingTemplate("ka00001", "{}", KiwoomAccountResponse.class);

    return pages.stream()
        .filter(page -> StringUtils.hasText(page.getAcctNo()))
        .flatMap(page -> Arrays.stream(page.getAcctNo().split(";")))
        .map(String::trim)
        .filter(accountNumber -> !accountNumber.isBlank())
        .map(accountNumber -> new AccountBalance(maskAccountNumber(accountNumber)))
        .toList();
  }

  @Override
  public DailyBalanceInfo fetchDailyBalance(String queryDate) {
    List<DailyBalanceResponse> pages = executePagingTemplate(
        "ka01690",
        new DailyBalanceRequest(queryDate),
        DailyBalanceResponse.class
    );

    DailyBalanceResponse firstPage = pages.getFirst();

    List<DailyBalanceInfo.StockItem> stockItems = pages.stream()
        .filter(page -> page.getDailyBalanceRate() != null)
        .flatMap(page -> page.getDailyBalanceRate().stream())
        .map(dto -> DailyBalanceInfo.StockItem.builder()
            .currentPrice(dto.getCurrentPrice())
            .stockCode(dto.getStockCode())
            .stockName(dto.getStockName())
            .remainderQuantity(dto.getRemainderQuantity())
            .buyUnitValue(dto.getBuyUnitValue())
            .buyWeight(dto.getBuyWeight())
            .evaluationProfit(dto.getEvaluationProfit())
            .profitRate(dto.getProfitRate())
            .evaluationAmount(dto.getEvaluationAmount())
            .evaluationWeight(dto.getEvaluationWeight())
            .build())
        .toList();

    return DailyBalanceInfo.builder()
        .date(firstPage.getDate())
        .totalBuyAmount(firstPage.getTotalBuyAmount())
        .totalEvaluationAmount(firstPage.getTotalEvaluationAmount())
        .totalEvaluationProfit(firstPage.getTotalEvaluationProfit())
        .totalProfitRate(firstPage.getTotalProfitRate())
        .depositBalance(firstPage.getDepositBalance())
        .dailyStockAsset(firstPage.getDailyStockAsset())
        .buyWeight(firstPage.getBuyWeight())
        .dailyBalanceRate(stockItems)
        .build();
  }

  @Override
  public AccountEvaluationInfo fetchAccountEvaluation(DomesticExchangeType domesticExchangeType) {
    List<AccountEvaluationResponse> pages = executePagingTemplate(
        "kt00004",
        AccountEvaluationRequest.defaultRequest(domesticExchangeType.name()),
        AccountEvaluationResponse.class
    );

    AccountEvaluationResponse firstPage = pages.getFirst();

    List<AccountEvaluationInfo.StockEvaluation> stockEvaluations = pages.stream()
        .filter(page -> page.getStockEvaluations() != null)
        .flatMap(page -> page.getStockEvaluations().stream())
        .map(dto -> AccountEvaluationInfo.StockEvaluation.builder()
            .stockCode(dto.getStockCode())
            .stockName(dto.getStockName())
            .remainingQuantity(dto.getRemainingQuantity())
            .averagePrice(dto.getAveragePrice())
            .currentPrice(dto.getCurrentPrice())
            .evaluationAmount(dto.getEvaluationAmount())
            .profitLossAmount(dto.getProfitLossAmount())
            .profitLossRate(dto.getProfitLossRate())
            .loanDate(dto.getLoanDate())
            .purchaseAmount(dto.getPurchaseAmount())
            .settlementRemaining(dto.getSettlementRemaining())
            .previousDayBuyQuantity(dto.getPreviousDayBuyQuantity())
            .previousDaySellQuantity(dto.getPreviousDaySellQuantity())
            .todayBuyQuantity(dto.getTodayBuyQuantity())
            .todaySellQuantity(dto.getTodaySellQuantity())
            .build())
        .toList();

    return AccountEvaluationInfo.builder()
        .accountName(firstPage.getAccountName())
        .branchName(firstPage.getBranchName())
        .deposit(firstPage.getDeposit())
        .d2Deposit(firstPage.getD2Deposit())
        .totalEstimatedAmount(firstPage.getTotalEstimatedAmount())
        .assetEvaluationAmount(firstPage.getAssetEvaluationAmount())
        .totalPurchaseAmount(firstPage.getTotalPurchaseAmount())
        .presumedDepositAssetAmount(firstPage.getPresumedDepositAssetAmount())
        .totalGuaranteeSellAmount(firstPage.getTotalGuaranteeSellAmount())
        .todayInvestmentPrincipal(firstPage.getTodayInvestmentPrincipal())
        .thisMonthInvestmentPrincipal(firstPage.getThisMonthInvestmentPrincipal())
        .accumulatedInvestmentPrincipal(firstPage.getAccumulatedInvestmentPrincipal())
        .todayProfitLoss(firstPage.getTodayProfitLoss())
        .thisMonthProfitLoss(firstPage.getThisMonthProfitLoss())
        .accumulatedProfitLoss(firstPage.getAccumulatedProfitLoss())
        .todayProfitRate(firstPage.getTodayProfitRate())
        .thisMonthProfitRate(firstPage.getThisMonthProfitRate())
        .accumulatedProfitRate(firstPage.getAccumulatedProfitRate())
        .stockEvaluations(stockEvaluations)
        .build();
  }

  private <T extends KiwoomResponse> List<T> executePagingTemplate(
      String apiId,
      Object requestBody,
      Class<T> responseType
  ) {
    String contYn = "N";
    String nextKey = "";
    Set<String> seenNextKeys = new HashSet<>();
    List<T> allPages = new ArrayList<>();

    for (int page = 1; page <= maxPages; page++) {
      ResponseEntity<T> responseEntity =
          requestWithTokenRefresh(apiId, requestBody, responseType, contYn, nextKey);
      T body = responseEntity.getBody();

      if (body == null) {
        throw new CustomException(ErrorCode.KIWOOM_EMPTY_RESPONSE);
      }

      validateKiwoomResponse(apiId, body);
      allPages.add(body);

      String responseContYn = responseEntity.getHeaders().getFirst("cont-yn");
      String responseNextKey = responseEntity.getHeaders().getFirst("next-key");
      log.info("[KiwoomAccount] 요청 완료: apiId={}, page={}, contYn={}", apiId, page, responseContYn);

      if (!"Y".equalsIgnoreCase(responseContYn)) {
        return allPages;
      }

      if (!StringUtils.hasText(responseNextKey) || !seenNextKeys.add(responseNextKey)) {
        throw new CustomException(ErrorCode.KIWOOM_PAGING_LIMIT_EXCEEDED);
      }

      contYn = "Y";
      nextKey = responseNextKey;
    }

    throw new CustomException(ErrorCode.KIWOOM_PAGING_LIMIT_EXCEEDED);
  }

  private <T extends KiwoomResponse> ResponseEntity<T> requestWithTokenRefresh(
      String apiId,
      Object requestBody,
      Class<T> responseType,
      String contYn,
      String nextKey
  ) {
    String token = tokenManager.getValidToken();

    try {
      return request(apiId, requestBody, responseType, contYn, nextKey, token);
    } catch (WebClientResponseException.Unauthorized e) {
      tokenManager.invalidateIfCurrent(token);
      log.warn("[KiwoomAccount] 인증 실패 후 토큰 재발급 재시도: apiId={}", apiId);
      return request(apiId, requestBody, responseType, contYn, nextKey, tokenManager.getValidToken());
    }
  }

  private <T extends KiwoomResponse> ResponseEntity<T> request(
      String apiId,
      Object requestBody,
      Class<T> responseType,
      String contYn,
      String nextKey,
      String token
  ) {
    try {
      return rateLimiter.call(() -> kiwoomWebClient.post()
          .uri(ACCOUNT_ENDPOINT)
          .header("authorization", "Bearer " + token)
          .header("api-id", apiId)
          .header("cont-yn", contYn)
          .header("next-key", nextKey == null ? "" : nextKey)
          .bodyValue(requestBody)
          .retrieve()
          .toEntity(responseType)
          .block(blockTimeout));
    } catch (WebClientResponseException.Unauthorized e) {
      throw e;
    } catch (WebClientResponseException e) {
      log.warn("[KiwoomAccount] HTTP 오류: apiId={}, status={}", apiId, e.getStatusCode());
      throw new CustomException(ErrorCode.KIWOOM_API_FAILED);
    } catch (WebClientRequestException | IllegalStateException e) {
      log.warn("[KiwoomAccount] 요청 실패: apiId={}, message={}", apiId, e.getMessage());
      throw new CustomException(ErrorCode.KIWOOM_API_FAILED);
    }
  }

  private void validateKiwoomResponse(String apiId, KiwoomResponse response) {
    String returnCode = response.returnCode();
    if (StringUtils.hasText(returnCode) && !"0".equals(returnCode.trim())) {
      log.warn("[KiwoomAccount] API 오류 응답: apiId={}, returnCode={}, returnMsg={}",
          apiId, returnCode, response.returnMsg());
      throw new CustomException(ErrorCode.KIWOOM_API_FAILED);
    }
  }

  private String maskAccountNumber(String accountNumber) {
    String value = accountNumber == null ? "" : accountNumber.trim();
    if (value.length() <= 4) {
      return "****";
    }

    return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
  }
}
