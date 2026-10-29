"""Additive US-GAAP mapping v2; existing metric identities retain their v1 version."""

# metric: (period kind, unit, ordered taxonomy aliases). Debt/EBITDA are derived,
# rather than silently equating operating income to EBIT or non-GAAP EBITDA.
METRICS = {
    "revenue": (
        "duration",
        "USD",
        [
            "RevenueFromContractWithCustomerExcludingAssessedTax",
            "Revenues",
            "SalesRevenueNet",
        ],
    ),
    "cost_of_revenue": (
        "duration",
        "USD",
        ["CostOfRevenue", "CostOfGoodsAndServicesSold"],
    ),
    "gross_profit": ("duration", "USD", ["GrossProfit"]),
    "operating_income": ("duration", "USD", ["OperatingIncomeLoss"]),
    "interest_expense": ("duration", "USD", ["InterestExpense"]),
    "income_tax_expense": ("duration", "USD", ["IncomeTaxExpenseBenefit"]),
    "income_before_tax": (
        "duration",
        "USD",
        [
            "IncomeLossFromContinuingOperationsBeforeIncomeTaxesExtraordinaryItemsNoncontrollingInterest"
        ],
    ),
    "net_income": ("duration", "USD", ["NetIncomeLoss"]),
    "eps_basic": ("duration", "USD/shares", ["EarningsPerShareBasic"]),
    "eps_diluted": ("duration", "USD/shares", ["EarningsPerShareDiluted"]),
    "weighted_average_shares_basic": (
        "duration",
        "shares",
        ["WeightedAverageNumberOfSharesOutstandingBasic"],
    ),
    "weighted_average_shares_diluted": (
        "duration",
        "shares",
        ["WeightedAverageNumberOfDilutedSharesOutstanding"],
    ),
    "cash_and_equivalents": (
        "instant",
        "USD",
        ["CashAndCashEquivalentsAtCarryingValue"],
    ),
    "short_term_investments": ("instant", "USD", ["ShortTermInvestments"]),
    "accounts_receivable": ("instant", "USD", ["AccountsReceivableNetCurrent"]),
    "inventory": ("instant", "USD", ["InventoryNet"]),
    "current_assets": ("instant", "USD", ["AssetsCurrent"]),
    "total_assets": ("instant", "USD", ["Assets"]),
    "goodwill": ("instant", "USD", ["Goodwill"]),
    "intangible_assets": ("instant", "USD", ["FiniteLivedIntangibleAssetsNet"]),
    "current_liabilities": ("instant", "USD", ["LiabilitiesCurrent"]),
    "short_term_debt": ("instant", "USD", ["ShortTermBorrowings"]),
    "current_long_term_debt": ("instant", "USD", ["LongTermDebtCurrent"]),
    "long_term_debt": ("instant", "USD", ["LongTermDebtNoncurrent"]),
    # Aggregate includes current maturities. Never alias this to noncurrent debt
    # or add current maturities to it again. SEC taxonomy excludes capital leases.
    "long_term_debt_including_current": ("instant", "USD", ["LongTermDebt"]),
    "total_liabilities": ("instant", "USD", ["Liabilities"]),
    "shareholders_equity": ("instant", "USD", ["StockholdersEquity"]),
    "minority_interest": ("instant", "USD", ["MinorityInterest"]),
    "preferred_equity": ("instant", "USD", ["PreferredStockValue"]),
    "shares_outstanding": ("instant", "shares", ["CommonStockSharesOutstanding"]),
    "operating_cash_flow": (
        "duration",
        "USD",
        ["NetCashProvidedByUsedInOperatingActivities"],
    ),
    "investing_cash_flow": (
        "duration",
        "USD",
        ["NetCashProvidedByUsedInInvestingActivities"],
    ),
    "financing_cash_flow": (
        "duration",
        "USD",
        ["NetCashProvidedByUsedInFinancingActivities"],
    ),
    "capital_expenditure": (
        "duration",
        "USD",
        ["PaymentsToAcquirePropertyPlantAndEquipment"],
    ),
    "acquisitions": (
        "duration",
        "USD",
        ["PaymentsToAcquireBusinessesNetOfCashAcquired"],
    ),
    "dividends_paid": ("duration", "USD", ["PaymentsOfDividends"]),
    "share_repurchases": ("duration", "USD", ["PaymentsForRepurchaseOfCommonStock"]),
    "share_issuance": ("duration", "USD", ["ProceedsFromStockIssuance"]),
    "depreciation": ("duration", "USD", ["Depreciation"]),
    "amortization": ("duration", "USD", ["AmortizationOfIntangibleAssets"]),
    "depreciation_amortization": ("duration", "USD", ["DepreciationAndAmortization"]),
    "depreciation_depletion_amortization": (
        "duration",
        "USD",
        ["DepreciationDepletionAndAmortization"],
    ),
    "stock_based_compensation": ("duration", "USD", ["ShareBasedCompensation"]),
    "research_and_development": ("duration", "USD", ["ResearchAndDevelopmentExpense"]),
}
MAPPING_VERSION = "sec_us_gaap_v2"
# Existing definitions/keys remain unchanged when adding a new metric.
METRIC_VERSIONS = {m: "sec_us_gaap_v1" for m in METRICS}
METRIC_VERSIONS["long_term_debt_including_current"] = "sec_us_gaap_v2"

# Separately identified components; never silently alias different definitions.
V3_METRICS = {
    "interest_expense_nonoperating": (
        "duration",
        "USD",
        ["InterestExpenseNonoperating"],
    ),
    "income_before_tax_domestic": (
        "duration",
        "USD",
        ["IncomeLossFromContinuingOperationsBeforeIncomeTaxesDomestic"],
    ),
    "income_before_tax_foreign": (
        "duration",
        "USD",
        ["IncomeLossFromContinuingOperationsBeforeIncomeTaxesForeign"],
    ),
    "debt_current_including_leases": ("instant", "USD", ["DebtCurrent"]),
    "current_debt_and_finance_leases": (
        "instant",
        "USD",
        ["LongTermDebtAndCapitalLeaseObligationsCurrent"],
    ),
    "noncurrent_debt_and_finance_leases": (
        "instant",
        "USD",
        ["LongTermDebtAndCapitalLeaseObligations"],
    ),
    "finance_lease_current": (
        "instant",
        "USD",
        ["FinanceLeaseLiabilityCurrent", "CapitalLeaseObligationsCurrent"],
    ),
    "finance_lease_noncurrent": (
        "instant",
        "USD",
        ["FinanceLeaseLiabilityNoncurrent", "CapitalLeaseObligationsNoncurrent"],
    ),
}
METRICS.update(V3_METRICS)
METRIC_VERSIONS.update({m: "sec_us_gaap_v3" for m in V3_METRICS})
MAPPING_VERSION = "sec_us_gaap_v3"
