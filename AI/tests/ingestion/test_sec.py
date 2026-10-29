"""Synthetic fixtures model documented SEC columnar submissions and companyfacts."""

import json
from datetime import datetime, timezone
from decimal import Decimal

import pytest

from AI.orchestration.sec import SecHttp, SourceError, parse_facts, parse_filings, utc

NOW = datetime(2026, 9, 23, tzinfo=timezone.utc)
CIK = "0000000001"
ACC = "0000000001-25-000001"


def submissions(amended=False):
    return {
        "cik": 1,
        "filings": {
            "files": [],
            "recent": {
                "accessionNumber": [ACC if not amended else "0000000001-25-000002"],
                "filingDate": ["2025-02-20" if not amended else "2025-03-20"],
                "reportDate": ["2024-12-31"],
                "acceptanceDateTime": [
                    "2025-02-20T21:00:00Z" if not amended else "2025-03-20T21:00:00Z"
                ],
                "form": ["10-K" if not amended else "10-K/A"],
                "primaryDocument": ["report.htm"],
            },
        },
    }


def companyfacts(amended=False):
    return {
        "cik": 1,
        "facts": {
            "us-gaap": {
                "Revenues": {
                    "units": {
                        "USD": [
                            {
                                "accn": ACC if not amended else "0000000001-25-000002",
                                "form": "10-K/A" if amended else "10-K",
                                "start": "2024-01-01",
                                "end": "2024-12-31",
                                "val": 110 if amended else 100,
                                "fy": 2024,
                                "fp": "FY",
                            }
                        ]
                    }
                }
            }
        },
    }


def test_acceptance_timestamp_and_delay():
    filings, bad = parse_filings(submissions(), CIK, "raw", NOW)
    assert not bad
    assert filings[0]["available_at"] == utc("2025-02-20T21:01:00Z")
    facts, bad = parse_facts(companyfacts(), CIK, filings, "raw")
    assert not bad and facts[0]["published_at"] == utc("2025-02-20T21:00:00Z")
    assert facts[0]["value"] == Decimal(100)


def test_missing_acceptance_never_falls_back_to_filed_date():
    data = submissions()
    data["filings"]["recent"]["acceptanceDateTime"] = [""]
    filings, bad = parse_filings(data, CIK, "raw", NOW)
    assert not filings and bad
    facts, bad = parse_facts(companyfacts(), CIK, filings, "raw")
    assert not facts and bad[0]["reason"] == "missing_accepted_filing"


def test_context_conflict_and_wrong_cik():
    filings, _ = parse_filings(submissions(), CIK, "raw", NOW)
    payload = companyfacts()
    entries = payload["facts"]["us-gaap"]["Revenues"]["units"]["USD"]
    entries.append({**entries[0], "val": 999})
    _, bad = parse_facts(payload, CIK, filings, "raw")
    assert bad[0]["reason"] == "conflicting_fact_context"
    with pytest.raises(ValueError):
        parse_facts(payload, "0000000002", filings, "raw")


class Response:
    def __init__(self, status, headers=None):
        self.status_code = status
        self.headers = headers or {}
        self.content = json.dumps({"status": status}).encode()


class Session:
    def __init__(self, responses):
        self.responses = iter(responses)
        self.calls = []

    def get(self, *args, **kwargs):
        self.calls.append((args, kwargs))
        return next(self.responses)


def test_http_archives_error_before_retry_and_honors_retry_after():
    session = Session([Response(429, {"Retry-After": "9"}), Response(200)])
    waits, raws = [], []
    client = SecHttp("Tests test@example.org", session, waits.append)

    def record(payload, metadata):
        raws.append((payload, metadata))
        return str(len(raws))

    result, raw = client.get("submissions/CIK0000000001.json", record)
    assert result["status"] == 200 and raw == "2"
    assert 9 in waits and len(raws) == 2
    assert all("User-Agent" not in metadata for _, metadata in raws)
    assert session.calls[0][1]["allow_redirects"] is False


def test_http_permanent_error_and_endpoint_guard():
    session = Session([Response(403)])
    client = SecHttp("Tests test@example.org", session, lambda _: None)
    with pytest.raises(SourceError):
        client.get("submissions/CIK0000000001.json", lambda *_: "raw")
    assert len(session.calls) == 1
    with pytest.raises(ValueError):
        client.get("https://other.example/secret", lambda *_: "raw")


def test_explicit_timezone_required():
    with pytest.raises(ValueError):
        utc("2025-02-20T21:00:00")
