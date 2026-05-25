package org.sejongisc.backend.assetmanagement.service;

import java.util.List;
import org.sejongisc.backend.assetmanagement.model.AccountBalance;
import org.sejongisc.backend.assetmanagement.model.AccountEvaluationInfo;
import org.sejongisc.backend.assetmanagement.model.DailyBalanceInfo;
import org.sejongisc.backend.assetmanagement.model.DomesticExchangeType;

public interface AssetManagementAccountClient {
  List<AccountBalance> fetchAccounts();

  DailyBalanceInfo fetchDailyBalance(String queryDate);

  AccountEvaluationInfo fetchAccountEvaluation(DomesticExchangeType domesticExchangeType);
}
