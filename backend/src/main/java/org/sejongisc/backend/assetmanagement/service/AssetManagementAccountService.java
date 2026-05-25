package org.sejongisc.backend.assetmanagement.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.assetmanagement.model.AccountBalance;
import org.sejongisc.backend.assetmanagement.model.AccountEvaluationInfo;
import org.sejongisc.backend.assetmanagement.model.DailyBalanceInfo;
import org.sejongisc.backend.assetmanagement.model.DomesticExchangeType;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetManagementAccountService {
  private static final String QUERY_DATE_PATTERN = "\\d{8}";

  private final AssetManagementAccountClient accountClient;

  public List<AccountBalance> getAccounts() {
    return accountClient.fetchAccounts();
  }

  public DailyBalanceInfo getDailyBalance(String queryDate) {
    if (queryDate == null || !queryDate.matches(QUERY_DATE_PATTERN)) {
      throw new CustomException(ErrorCode.INVALID_KIWOOM_QUERY_DATE);
    }

    return accountClient.fetchDailyBalance(queryDate);
  }

  public AccountEvaluationInfo getAccountEvaluation(String exchangeType) {
    return accountClient.fetchAccountEvaluation(DomesticExchangeType.from(exchangeType));
  }
}
