"""PIT feature calculations with explicit intervals, missingness and input lineage."""

from datetime import timedelta
from decimal import Decimal

from AI.orchestration.metrics import METRICS
from AI.orchestration.sec import utc
from AI.orchestration.source_reviews import debt_review


def feature(name, value, inputs, method, status="valid", **extra):
    return {
        "metric_id": name,
        "value": value,
        "status": status,
        "input_issues": [
            {
                "metric_id": i.get("metric_id", "external_input"),
                "status": i.get("status", "source_observation"),
                "period_start": i.get("period_start"),
                "period_end": i.get("period_end"),
                "missing_metrics": i.get("missing_metrics", []),
            }
            for i in inputs
        ]
        if value is None
        else [],
        "missing_metrics": sorted(
            {
                m
                for i in inputs
                if i.get("value") is None
                for m in i.get(
                    "missing_metrics", [i.get("metric_id", "external_input")]
                )
            }
        )
        if inputs
        else ([name] if value is None else []),
        "calculation_version": method,
        "available_at": max(
            (r["available_at"] for r in inputs if r.get("available_at") is not None),
            default=None,
        ),
        "source_fact_ids": sorted(
            {
                str(fid)
                for r in inputs
                for fid in r.get("source_fact_ids", [r.get("fact_id")])
                if fid
            }
        ),
        **extra,
    }


def quarters(facts, metric, *, as_of):
    if (
        metric not in METRICS
        or METRICS[metric][0] != "duration"
        or metric.startswith(("eps_", "weighted_average_"))
    ):
        raise ValueError("Standalone differencing requires an additive duration metric")
    cutoff = utc(as_of)
    rows = [
        r for r in facts if r["metric_id"] == metric and r["available_at"] <= cutoff
    ]
    # Defensively deduplicate callers not coming from the SQL SDK.
    selected = {}
    for r in sorted(rows, key=lambda r: (r["available_at"], -r.get("tag_priority", 0))):
        selected[(r["period_start"], r["period_end"], r["unit"])] = r
    rows = list(selected.values())
    result = {}
    for r in sorted(rows, key=lambda r: (r["period_end"], r["period_start"])):
        days = (r["period_end"] - r["period_start"]).days + 1
        if 60 <= days <= 120:
            key = (r["period_start"], r["period_end"], r["unit"])
            if key not in result or result[key]["available_at"] <= r["available_at"]:
                result[key] = feature(
                    metric,
                    Decimal(r["value"]),
                    [r],
                    "reported_quarter_v1",
                    period_start=r["period_start"],
                    period_end=r["period_end"],
                    unit=r["unit"],
                    calculated_quarter=False,
                )
        elif 120 < days <= 400:
            previous = [
                p
                for p in rows
                if p["period_start"] == r["period_start"]
                and p["unit"] == r["unit"]
                and 60 <= (r["period_end"] - p["period_end"]).days <= 120
            ]
            if not previous:
                continue
            p = max(previous, key=lambda p: (p["period_end"], p["available_at"]))
            start = p["period_end"] + timedelta(days=1)
            key = (start, r["period_end"], r["unit"])
            derived = feature(
                metric,
                Decimal(r["value"]) - Decimal(p["value"]),
                [r, p],
                "ytd_difference_v1",
                period_start=start,
                period_end=r["period_end"],
                unit=r["unit"],
                calculated_quarter=True,
            )
            # Explicit standalone fact is preferred, unless a later filing revised the cumulative figures.
            if (
                key not in result
                or result[key]["available_at"] < derived["available_at"]
            ):
                result[key] = derived
    return sorted(result.values(), key=lambda r: (r["period_end"], r["period_start"]))


def ttm(facts, metric, *, as_of, period_end=None):
    if period_end is not None:
        facts = [f for f in facts if f["period_end"] <= period_end]
    rows = quarters(facts, metric, as_of=as_of)
    cutoff = utc(as_of)
    annual = [
        f
        for f in facts
        if f["metric_id"] == metric
        and f["available_at"] <= cutoff
        and 350 <= (f["period_end"] - f["period_start"]).days + 1 <= 380
    ]
    latest_end = max(
        [f["period_end"] for f in annual] + [r["period_end"] for r in rows],
        default=None,
    )
    if period_end is not None and latest_end != period_end:
        return feature(metric, None, [], "ttm_v3", "missing_target_period")
    annual = [f for f in annual if f["period_end"] == latest_end]
    if annual:
        latest_publication = max(f["available_at"] for f in annual)
        annual = [f for f in annual if f["available_at"] == latest_publication]
        priority = min(f.get("tag_priority", 0) for f in annual)
        annual = [f for f in annual if f.get("tag_priority", 0) == priority]
        signatures = {
            (f["period_start"], f["period_end"], f["unit"], Decimal(f["value"]))
            for f in annual
        }
        if len(signatures) != 1:
            return feature(
                metric,
                None,
                annual,
                "ttm_reported_annual_v2",
                "ambiguous_annual_period",
            )
        selected = annual[0]
        revised_quarters = [
            r
            for r in rows
            if selected["period_start"] <= r["period_start"]
            and r["period_end"] <= selected["period_end"]
            and r["available_at"] > selected["available_at"]
        ]
        if not revised_quarters:
            return feature(
                metric,
                Decimal(selected["value"]),
                [selected],
                "ttm_reported_annual_v2",
                period_start=selected["period_start"],
                period_end=selected["period_end"],
                unit=selected["unit"],
            )
    if not rows:
        return feature(metric, None, [], "ttm_four_quarters_v1", "missing_quarters")
    latest = max(r["period_end"] for r in rows)
    if latest != latest_end:
        return feature(
            metric, None, rows, "ttm_four_quarters_v2", "stale_quarter_chain"
        )
    chain = []
    for _ in range(4):
        candidates = [r for r in rows if r["period_end"] == latest]
        if len(candidates) != 1:
            return feature(
                metric, None, chain, "ttm_four_quarters_v1", "noncomparable_quarters"
            )
        row = candidates[0]
        chain.append(row)
        latest = row["period_start"] - timedelta(days=1)
    if (
        len({r["unit"] for r in chain}) != 1
        or not 350
        <= (chain[0]["period_end"] - chain[-1]["period_start"]).days + 1
        <= 380
    ):
        return feature(
            metric, None, chain, "ttm_four_quarters_v1", "noncomparable_quarters"
        )
    return feature(
        metric,
        sum(r["value"] for r in chain),
        chain,
        "ttm_four_quarters_v1",
        period_start=chain[-1]["period_start"],
        period_end=chain[0]["period_end"],
        unit=chain[0]["unit"],
    )


def combine(name, inputs, formula, version, *, positive_denominator=False):
    if any(i is None or i["value"] is None for i in inputs):
        return feature(
            name, None, [i for i in inputs if i is not None], version, "missing_inputs"
        )
    # Metrics from different fiscal windows must not be blended.
    windows = {(i.get("period_start"), i.get("period_end")) for i in inputs}
    if len(windows) != 1 or len({i.get("unit") for i in inputs}) != 1:
        return feature(name, None, inputs, version, "unaligned_inputs")
    if positive_denominator and inputs[-1]["value"] <= 0:
        return feature(name, None, inputs, version, "nonpositive_denominator")
    return feature(
        name,
        formula(*[i["value"] for i in inputs]),
        inputs,
        version,
        period_start=inputs[0].get("period_start"),
        period_end=inputs[0].get("period_end"),
        unit=inputs[0].get("unit"),
    )


def financial_features(facts, *, as_of, industry_model):
    if industry_model != "industrial":
        raise ValueError(
            "Only explicit industrial model is supported; banks/insurance/REIT need separate definitions"
        )
    cutoff = utc(as_of)
    facts = [f for f in facts if f["available_at"] <= cutoff]
    balance_end = max(
        (f["period_end"] for f in facts if f["metric_id"] == "total_assets"),
        default=None,
    )
    flows = {
        m: ttm(facts, m, as_of=cutoff, period_end=balance_end)
        for m in [
            "operating_cash_flow",
            "capital_expenditure",
            "operating_income",
            "income_before_tax",
            "income_before_tax_domestic",
            "income_before_tax_foreign",
            "income_tax_expense",
            "interest_expense",
            "interest_expense_nonoperating",
            "depreciation_amortization",
            "depreciation",
            "amortization",
        ]
    }
    if flows["income_before_tax"]["value"] is None:
        flows["income_before_tax"] = combine(
            "income_before_tax",
            [flows["income_before_tax_domestic"], flows["income_before_tax_foreign"]],
            lambda a, b: a + b,
            "pretax_domestic_plus_foreign_v3",
        )
    fcf = combine(
        "fcf",
        [flows["operating_cash_flow"], flows["capital_expenditure"]],
        lambda a, b: a - b,
        "fcf_ocf_minus_positive_capex_v3",
    )
    if (
        flows["capital_expenditure"]["value"] is not None
        and flows["capital_expenditure"]["value"] < 0
    ):
        fcf.update(value=None, status="negative_capex_outflow")
    interest = flows["interest_expense"]
    if interest["value"] is None:
        interest = flows["interest_expense_nonoperating"]
    ebit = combine(
        "ebit",
        [flows["income_before_tax"], interest],
        lambda a, b: a + b,
        "ebit_pretax_plus_nonoperating_interest_v3"
        if interest["metric_id"] == "interest_expense_nonoperating"
        else "ebit_pretax_plus_interest_v3",
    )
    da = flows["depreciation_amortization"]
    if da["value"] is None:
        da = combine(
            "depreciation_amortization",
            [flows["depreciation"], flows["amortization"]],
            lambda a, b: a + b,
            "da_sum_v3",
        )
    base, version = ebit, "ebitda_ebit_plus_da_v3"
    if base["value"] is None:
        base, version = flows["operating_income"], "ebitda_operating_income_plus_da_v3"
    ebitda = combine(
        "ebitda_calculated_standard", [base, da], lambda a, b: a + b, version
    )
    instants = [f for f in facts if METRICS[f["metric_id"]][0] == "instant"]
    # Shares outstanding can be measured weeks after the balance-sheet date.
    # They must never move the date used for debt, cash and invested capital.
    balances = [f for f in instants if f["metric_id"] != "shares_outstanding"]
    assets = [f for f in balances if f["metric_id"] == "total_assets"]
    end = max((f["period_end"] for f in (assets or balances)), default=None)

    def instant(metric, period):
        matched = [
            f
            for f in instants
            if f["metric_id"] == metric and f["period_end"] == period
        ]
        if not matched:
            return feature(metric, None, [], "instant_v3", "missing_inputs")
        f = max(matched, key=lambda f: (f["available_at"], -f.get("tag_priority", 0)))
        return feature(
            metric,
            Decimal(f["value"]),
            [f],
            "instant_v3",
            period_start=period,
            period_end=period,
            unit=f["unit"],
        )

    def capital(period):
        current = instant("current_long_term_debt", period)
        noncurrent = instant("long_term_debt", period)
        short = instant("short_term_debt", period)

        def less_leases(metric, combined, lease):
            result = combine(
                metric,
                [instant(combined, period), instant(lease, period)],
                lambda a, b: a - b,
                "debt_less_explicit_finance_leases_v3",
            )
            if result["value"] is not None and result["value"] < 0:
                result.update(value=None, status="negative_derived_debt")
            return result

        if current["value"] is None:
            current = less_leases(
                "current_long_term_debt",
                "current_debt_and_finance_leases",
                "finance_lease_current",
            )
        if noncurrent["value"] is None:
            noncurrent = less_leases(
                "long_term_debt",
                "noncurrent_debt_and_finance_leases",
                "finance_lease_noncurrent",
            )
        if short["value"] is None:
            short = combine(
                "short_term_debt",
                [
                    instant("debt_current_including_leases", period),
                    current,
                    instant("finance_lease_current", period),
                ],
                lambda a, b, c: a - b - c,
                "short_debt_from_current_less_long_and_leases_v3",
            )
            if short["value"] is not None and short["value"] < 0:
                short.update(value=None, status="negative_derived_debt")
        debt = combine(
            "total_debt",
            [short, current, noncurrent],
            lambda a, b, c: a + b + c,
            "debt_excluding_leases_v3",
        )
        aggregate = combine(
            "total_debt",
            [
                short,
                instant("long_term_debt_including_current", period),
            ],
            lambda a, b: a + b,
            "debt_short_plus_total_long_excluding_leases_v3",
        )
        if debt["value"] is None:
            debt = aggregate
        elif aggregate["value"] is not None and aggregate["value"] != debt["value"]:
            review = debt_review(facts, period)
            if review:
                reviewed_inputs = [
                    instant(m, period) for m in review.get("weights", {})
                ]
                resolved_value = (
                    sum(
                        i["value"] * review["weights"][i["metric_id"]]
                        for i in reviewed_inputs
                    )
                    if reviewed_inputs
                    else debt["value"]
                )
                debt = feature(
                    "total_debt",
                    resolved_value,
                    [debt, aggregate, *reviewed_inputs],
                    "debt_reviewed_carrying_components_v3",
                    period_start=period,
                    period_end=period,
                    unit="USD",
                    source_review=review,
                )
            else:
                debt = feature(
                    "total_debt",
                    None,
                    [debt, aggregate],
                    "debt_excluding_leases_v3",
                    "conflicting_debt_totals",
                )
        net = combine(
            "net_debt",
            [debt, instant("cash_and_equivalents", period)],
            lambda a, b: a - b,
            "net_debt_excluding_leases_v3",
        )
        ic = combine(
            "invested_capital",
            [net, instant("shareholders_equity", period)],
            lambda a, b: a + b,
            "ic_debt_equity_less_cash_v3",
        )
        return debt, net, ic

    debt, net, ic = capital(end)
    roic = feature(
        "roic",
        None,
        [ebit, flows["income_before_tax"], flows["income_tax_expense"], ic],
        "roic_nopat_avg_ic_v3",
        "missing_inputs",
    )
    pretax, tax = flows["income_before_tax"], flows["income_tax_expense"]
    if (
        ebit["value"] is not None
        and pretax["value"] is not None
        and tax["value"] is not None
    ):
        previous_end = ebit["period_start"] - timedelta(days=1)
        _, _, previous_ic = capital(previous_end)
        inputs = [ebit, pretax, tax, ic, previous_ic]
        roic = feature("roic", None, inputs, "roic_nopat_avg_ic_v3", "missing_inputs")
        if (
            all(i["value"] is not None for i in inputs)
            and end == ebit["period_end"]
            and pretax["value"] > 0
        ):
            rate = tax["value"] / pretax["value"]
            average = (ic["value"] + previous_ic["value"]) / 2
            aligned = all(
                (f.get("period_start"), f.get("period_end"))
                == (ebit["period_start"], ebit["period_end"])
                for f in [pretax, tax]
            )
            if aligned and 0 <= rate <= 1 and average > 0:
                roic = feature(
                    "roic",
                    ebit["value"] * (1 - rate) / average,
                    inputs,
                    "roic_nopat_avg_ic_v3",
                    unit="ratio",
                )
            else:
                roic = feature(
                    "roic",
                    None,
                    inputs,
                    "roic_nopat_avg_ic_v3",
                    "invalid_tax_rate_capital_or_alignment",
                )
    return [fcf, ebit, ebitda, debt, net, roic]


def valuation_features(*, market_cap, debt, preferred, minority, cash, ebitda, as_of):
    """Explicit sourced observations; absence of preferred/minority is NOT zero.

    Each observation carries value, unit, available_at, period_end and source_fact_ids.
    Market cap is a separate PIT input; do not multiply today's shares by old prices.
    """
    cutoff = utc(as_of)
    inputs = [market_cap, debt, preferred, minority, cash, ebitda]
    inputs = [
        dict(i, available_at=utc(i["available_at"]))
        if i is not None and i.get("available_at") is not None
        else i
        for i in inputs
    ]
    market_cap, debt, preferred, minority, cash, ebitda = inputs
    visible = [i for i in inputs if i is not None]
    if any(
        i.get("available_at") is not None and utc(i["available_at"]) > cutoff
        for i in visible
    ):
        raise ValueError("Valuation input is not available at as_of")
    if any(
        i is None or i.get("value") is None or i.get("available_at") is None
        for i in inputs
    ):
        return [
            feature(
                "enterprise_value",
                None,
                visible,
                "ev_debt_preferred_minority_less_cash_v1",
                "missing_inputs",
            ),
            feature(
                "ev_ebitda", None, visible, "ev_ebitda_standard_v1", "missing_inputs"
            ),
        ]
    for i in inputs:
        if not Decimal(i["value"]).is_finite() or i.get("unit") != "USD":
            raise ValueError("Finite USD valuation inputs required")
    if len({i.get("period_end") for i in [debt, preferred, minority, cash]}) != 1:
        raise ValueError("Balance sheet dates must align")
    value = (
        Decimal(market_cap["value"])
        + Decimal(debt["value"])
        + Decimal(preferred["value"])
        + Decimal(minority["value"])
        - Decimal(cash["value"])
    )
    ev = feature(
        "enterprise_value",
        value,
        inputs[:-1],
        "ev_debt_preferred_minority_less_cash_v1",
        unit="USD",
    )
    if Decimal(ebitda["value"]) <= 0:
        ratio = feature(
            "ev_ebitda",
            None,
            inputs,
            "ev_ebitda_standard_v1",
            "nonpositive_denominator",
        )
    else:
        ratio = feature(
            "ev_ebitda",
            value / Decimal(ebitda["value"]),
            inputs,
            "ev_ebitda_standard_v1",
            unit="ratio",
        )
    return [ev, ratio]
