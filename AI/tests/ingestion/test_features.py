from datetime import date
from decimal import Decimal

import pytest

from AI.orchestration.features import financial_features, quarters, ttm
from AI.orchestration.sec import utc


def fact(metric, start, end, value, available="2025-02-20T21:01:00Z"):
    return {
        "metric_id": metric,
        "period_start": date.fromisoformat(start),
        "period_end": date.fromisoformat(end),
        "value": Decimal(value),
        "available_at": utc(available),
        "unit": "USD",
        "fact_id": f"{metric}:{start}:{end}:{value}",
    }


def annual_flow(metric, values):
    return [
        fact(metric, "2024-01-01", end, v)
        for end, v in zip(
            ["2024-03-31", "2024-06-30", "2024-09-30", "2024-12-31"], values
        )
    ]


def test_ytd_to_quarters_and_ttm():
    facts = annual_flow("operating_cash_flow", ["10", "25", "45", "70"])
    rows = quarters(facts, "operating_cash_flow", as_of="2025-03-01T00:00:00Z")
    assert [r["value"] for r in rows] == [10, 15, 20, 25]
    assert rows[-1]["calculated_quarter"] is True
    assert (
        ttm(facts, "operating_cash_flow", as_of="2025-03-01T00:00:00Z")["value"] == 70
    )
    assert (
        ttm(facts, "operating_cash_flow", as_of="2025-01-01T00:00:00Z")["value"] is None
    )


def test_gap_and_nonadditive_metrics_never_produce_ttm():
    rows = annual_flow("operating_cash_flow", ["10", "25", "45", "70"])
    rows.pop(1)
    rows.pop()  # Without a reported annual total, the quarter gap remains fatal.
    assert (
        ttm(rows, "operating_cash_flow", as_of="2025-03-01T00:00:00Z")["value"] is None
    )
    with pytest.raises(ValueError):
        quarters([], "weighted_average_shares_basic", as_of="2025-03-01T00:00:00Z")


def test_later_ytd_revision_overrides_old_direct_quarter():
    rows = annual_flow("operating_cash_flow", ["10", "25", "45", "70"])
    rows.append(fact("operating_cash_flow", "2024-04-01", "2024-06-30", "15"))
    rows.append(
        fact(
            "operating_cash_flow",
            "2024-01-01",
            "2024-06-30",
            "30",
            "2025-04-01T00:00:00Z",
        )
    )
    before = quarters(rows, "operating_cash_flow", as_of="2025-03-01T00:00:00Z")
    after = quarters(rows, "operating_cash_flow", as_of="2025-05-01T00:00:00Z")
    assert before[1]["value"] == 15 and after[1]["value"] == 20
    assert after[1]["available_at"] == utc("2025-04-01T00:00:00Z")


def test_fcf_ebitda_and_missing_debt_flags():
    facts = annual_flow("operating_cash_flow", ["10", "20", "30", "40"])
    facts += annual_flow("capital_expenditure", ["1", "2", "3", "4"])
    facts += annual_flow("operating_income", ["5", "10", "15", "20"])
    facts += annual_flow("depreciation_amortization", ["2", "4", "6", "8"])
    output = {
        r["metric_id"]: r
        for r in financial_features(
            facts, as_of="2025-03-01T00:00:00Z", industry_model="industrial"
        )
    }
    assert output["fcf"]["value"] == 36
    assert output["ebitda_calculated_standard"]["value"] == 28
    assert (
        output["ebitda_calculated_standard"]["calculation_version"]
        == "ebitda_operating_income_plus_da_v3"
    )
    assert output["net_debt"]["value"] is None
    assert output["net_debt"]["status"] == "missing_inputs"


def test_roic_average_capital():
    facts = []
    for metric, values in [
        ("income_before_tax", ["10", "20", "30", "40"]),
        ("interest_expense", ["1", "2", "3", "4"]),
        ("income_tax_expense", ["2", "4", "6", "8"]),
    ]:
        facts += annual_flow(metric, values)
    for end in ["2023-12-31", "2024-12-31"]:
        for metric, value in [
            ("short_term_debt", "10"),
            ("current_long_term_debt", "10"),
            ("long_term_debt", "30"),
            ("cash_and_equivalents", "10"),
            ("shareholders_equity", "60"),
        ]:
            facts.append(fact(metric, end, end, value))
    output = {
        r["metric_id"]: r
        for r in financial_features(
            facts, as_of="2025-03-01T00:00:00Z", industry_model="industrial"
        )
    }
    assert output["net_debt"]["value"] == 40
    assert output["roic"]["value"] == Decimal("0.352")
    assert len(output["roic"]["source_fact_ids"]) > 5


def test_ev_ebitda_and_nonpositive_denominator():
    from AI.orchestration.features import valuation_features

    def observed(value):
        return {
            "value": Decimal(value),
            "unit": "USD",
            "available_at": utc("2025-02-20T21:01:00Z"),
            "period_end": date(2024, 12, 31),
            "source_fact_ids": ["source"],
        }

    args = {
        "market_cap": observed("100"),
        "debt": observed("20"),
        "preferred": observed("0"),
        "minority": observed("0"),
        "cash": observed("10"),
        "ebitda": observed("10"),
        "as_of": "2025-03-01T00:00:00Z",
    }
    ev, ratio = valuation_features(**args)
    assert ev["value"] == 110 and ratio["value"] == 11
    args["market_cap"] = {
        **args["market_cap"],
        "available_at": "2025-02-21T01:00:00+09:00",
    }
    ev, ratio = valuation_features(**args)
    assert ev["available_at"] == utc("2025-02-20T21:01:00Z")
    args["ebitda"] = observed("-10")
    assert valuation_features(**args)[1]["status"] == "nonpositive_denominator"
    args["cash"] = None
    assert valuation_features(**args)[1]["value"] is None


def test_future_valuation_input_rejected():
    from AI.orchestration.features import valuation_features

    observation = {
        "value": Decimal(10),
        "unit": "USD",
        "available_at": utc("2025-04-01T00:00:00Z"),
        "period_end": date(2024, 12, 31),
        "source_fact_ids": ["source"],
    }
    with pytest.raises(ValueError):
        valuation_features(
            market_cap=observation,
            debt=observation,
            preferred=observation,
            minority=observation,
            cash=observation,
            ebitda=observation,
            as_of="2025-03-01T00:00:00Z",
        )


def test_reported_annual_is_ttm_without_interim_quarters():
    row = fact("operating_cash_flow", "2024-01-01", "2024-12-31", "70")
    result = ttm([row], "operating_cash_flow", as_of="2025-03-01T00:00:00Z")
    assert result["value"] == 70
    assert result["calculation_version"] == "ttm_reported_annual_v2"
    assert result["source_fact_ids"] == [row["fact_id"]]
    assert (
        ttm([row], "operating_cash_flow", as_of="2025-01-01T00:00:00Z")["value"] is None
    )


def test_newer_incomplete_quarter_cannot_fall_back_to_stale_annual():
    facts = [
        fact("operating_cash_flow", "2023-01-01", "2023-12-31", "100"),
        fact("operating_cash_flow", "2024-01-01", "2024-03-31", "20"),
    ]
    assert (
        ttm(facts, "operating_cash_flow", as_of="2025-03-01T00:00:00Z")["value"] is None
    )


def test_ambiguous_annual_periods_are_not_arbitrarily_selected():
    facts = [
        fact("operating_cash_flow", "2024-01-01", "2024-12-31", "100"),
        fact("operating_cash_flow", "2023-12-31", "2024-12-31", "101"),
    ]
    assert (
        ttm(facts, "operating_cash_flow", as_of="2025-03-01T00:00:00Z")["status"]
        == "ambiguous_annual_period"
    )


def test_share_measurement_date_does_not_hide_balance_sheet():
    facts = [
        fact(m, "2024-12-31", "2024-12-31", v)
        for m, v in [
            ("total_assets", "100"),
            ("short_term_debt", "5"),
            ("current_long_term_debt", "10"),
            ("long_term_debt", "30"),
            ("cash_and_equivalents", "20"),
        ]
    ]
    facts.append(fact("shares_outstanding", "2025-02-01", "2025-02-01", "500"))
    result = {
        r["metric_id"]: r
        for r in financial_features(
            facts, as_of="2025-03-01T00:00:00Z", industry_model="industrial"
        )
    }
    assert result["net_debt"]["value"] == 25
    assert result["net_debt"]["period_end"] == date(2024, 12, 31)
    assert facts[-1]["fact_id"] not in result["net_debt"]["source_fact_ids"]


def test_aggregate_long_debt_does_not_double_count_current_maturities():
    facts = [
        fact(m, "2024-12-31", "2024-12-31", v)
        for m, v in [
            ("short_term_debt", "5"),
            ("current_long_term_debt", "10"),
            ("long_term_debt_including_current", "40"),
            ("cash_and_equivalents", "20"),
        ]
    ]

    def output(rows):
        return {
            r["metric_id"]: r
            for r in financial_features(
                rows, as_of="2025-03-01T00:00:00Z", industry_model="industrial"
            )
        }

    assert output(facts)["total_debt"]["value"] == 45
    assert output(facts)["net_debt"]["value"] == 25
    assert (
        output(facts[1:])["total_debt"]["value"] is None
    )  # Missing short-term debt is not zero.
    facts.append(fact("long_term_debt", "2024-12-31", "2024-12-31", "30"))
    assert output(facts)["total_debt"]["value"] == 45
    facts[-1]["value"] = Decimal("35")
    assert output(facts)["total_debt"]["status"] == "conflicting_debt_totals"


def test_53_week_annual_and_later_partial_restatement_policy():
    row = fact("operating_cash_flow", "2023-12-31", "2025-01-04", "100")
    assert (
        ttm([row], "operating_cash_flow", as_of="2025-03-01T00:00:00Z")["value"] == 100
    )
    rows = [
        fact("operating_cash_flow", "2024-01-01", "2024-12-31", "100"),
        fact(
            "operating_cash_flow",
            "2024-04-01",
            "2024-06-30",
            "40",
            "2025-04-01T00:00:00Z",
        ),
    ]
    # A later standalone quarter correction prevents silently reusing the old annual total.
    rows.extend(
        fact("operating_cash_flow", start, end, "20")
        for start, end in [
            ("2023-07-01", "2023-09-30"),
            ("2023-10-01", "2023-12-31"),
            ("2024-01-01", "2024-03-31"),
        ]
    )
    assert (
        ttm(rows, "operating_cash_flow", as_of="2025-05-01T00:00:00Z")["status"]
        == "stale_quarter_chain"
    )
    assert (
        ttm(rows, "operating_cash_flow", as_of="2025-05-01T00:00:00Z")["value"] is None
    )
