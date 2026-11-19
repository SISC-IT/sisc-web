"""Official SEC JSON -> immutable Bronze -> filing-linked US GAAP facts."""

import json
import logging
import os
import random
import re
import time
import uuid
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from email.utils import parsedate_to_datetime

from AI.orchestration.core import content_hash
from AI.orchestration.metrics import MAPPING_VERSION, METRIC_VERSIONS, METRICS

LOG = logging.getLogger(__name__)
FORMS = {"10-K", "10-K/A", "10-Q", "10-Q/A"}


def utc(value):
    result = (
        datetime.fromisoformat(value.replace("Z", "+00:00"))
        if isinstance(value, str)
        else value
    )
    if not isinstance(result, datetime) or result.tzinfo is None:
        raise ValueError("Timezone-aware timestamp required")
    return result.astimezone(timezone.utc)


def cik_string(value):
    text = str(value)
    if not text.isdigit() or not 0 < int(text) < 10**10:
        raise ValueError("Invalid SEC CIK")
    return text.zfill(10)


class SourceError(RuntimeError):
    pass


class SecHttp:
    """Use within the global SEC source lock; max 5 requests/sec, no redirects."""

    def __init__(self, user_agent=None, session=None, sleep=time.sleep):
        import requests

        self.user_agent = user_agent or os.environ.get("SEC_USER_AGENT", "")
        if (
            not re.search(r"\S+@\S+\.\S+", self.user_agent)
            or "\n" in self.user_agent
            or "\r" in self.user_agent
        ):
            raise ValueError(
                "SEC_USER_AGENT must identify the organization and contact email"
            )
        self.session = session or requests.Session()
        self.sleep = sleep
        self.last_request = 0

    def get(self, path, record, heartbeat=lambda attempt: None):
        import requests

        if not re.fullmatch(
            r"(submissions/[A-Za-z0-9-]+\.json|api/xbrl/companyfacts/CIK[0-9]{10}\.json)",
            path,
        ):
            raise ValueError("Unsupported SEC endpoint")
        for attempt in range(1, 4):
            heartbeat(attempt)
            self.sleep(max(0, self.last_request + 0.2 - time.monotonic()))
            self.last_request = time.monotonic()
            try:
                response = self.session.get(
                    "https://data.sec.gov/" + path,
                    headers={
                        "User-Agent": self.user_agent,
                        "Accept": "application/json",
                    },
                    timeout=(10, 45),
                    allow_redirects=False,
                )
            except (requests.Timeout, requests.ConnectionError):
                if attempt == 3:
                    raise SourceError("SEC transport failed") from None
                self.sleep(2**attempt)
                continue
            # Preserve even non-200 payloads; no request headers/contact information in metadata.
            raw_id = record(
                response.content,
                {
                    "path": path,
                    "status": response.status_code,
                    "etag": response.headers.get("ETag"),
                    "last_modified": response.headers.get("Last-Modified"),
                },
            )
            if response.status_code == 200:
                return json.loads(response.content, parse_float=Decimal), raw_id
            if response.status_code != 429 and not 500 <= response.status_code < 600:
                raise SourceError("SEC non-retryable HTTP status")
            if attempt == 3:
                raise SourceError("SEC retry budget exhausted")
            delay = 2**attempt + random.random()
            retry = response.headers.get("Retry-After")
            if retry:
                try:
                    delay = max(delay, float(retry))
                except ValueError:
                    delay = max(
                        delay,
                        (
                            parsedate_to_datetime(retry) - datetime.now(timezone.utc)
                        ).total_seconds(),
                    )
            if delay > 120:
                # Defer to next scheduled run instead of retrying earlier than Retry-After.
                raise SourceError("SEC deferred by Retry-After")
            self.sleep(delay)
        raise SourceError("SEC fetch failed")


def parse_filings(data, cik, raw_id, now, delay_seconds=60):
    if not 0 <= delay_seconds <= 86400:
        raise ValueError("Invalid SEC availability delay")
    columns = data["filings"]["recent"] if "filings" in data else data
    keys = [
        "accessionNumber",
        "filingDate",
        "reportDate",
        "acceptanceDateTime",
        "form",
        "primaryDocument",
    ]
    count = len(columns["accessionNumber"])
    if any(len(columns[k]) != count for k in keys):
        raise ValueError("SEC submissions column lengths differ")
    rows, rejected = [], []
    for i in range(count):
        if columns["form"][i] not in FORMS:
            continue
        accession = columns["accessionNumber"][i]
        try:
            if not re.fullmatch(r"\d{10}-\d{2}-\d{6}", accession):
                raise ValueError("Invalid accession")
            accepted = utc(columns["acceptanceDateTime"][i])
            if accepted > now:
                raise ValueError("Future acceptance")
            rows.append(
                {
                    "filing_id": str(
                        uuid.uuid5(uuid.NAMESPACE_URL, "sec:" + accession)
                    ),
                    "cik": cik,
                    "accession_number": accession,
                    "form_type": columns["form"][i],
                    "period_end": date.fromisoformat(columns["reportDate"][i])
                    if columns["reportDate"][i]
                    else None,
                    "filed_date": date.fromisoformat(columns["filingDate"][i]),
                    "accepted_at": accepted,
                    "available_at": accepted + timedelta(seconds=delay_seconds),
                    "is_amendment": columns["form"][i].endswith("/A"),
                    "primary_document": columns["primaryDocument"][i],
                    "raw_id": raw_id,
                }
            )
        except (ValueError, TypeError):
            rejected.append(
                {
                    "accession": accession,
                    "reason": "unverified_filing_timestamp_or_schema",
                }
            )
    return rows, rejected


def parse_facts(
    data, cik, filings, raw_id, *, period_end_start=None, period_end_end=None
):
    if cik_string(data["cik"]) != cik:
        raise ValueError("Companyfacts CIK mismatch")
    by_accession = {f["accession_number"]: f for f in filings}
    gaap = data["facts"].get("us-gaap", {})
    facts, rejected, seen = [], [], {}
    for metric, (kind, unit, tags) in METRICS.items():
        for priority, tag in enumerate(tags):
            for item in gaap.get(tag, {}).get("units", {}).get(unit, []):
                if item.get("form") not in FORMS:
                    continue
                # Explicit observation scope is applied before filing linkage.
                # Archive still contains the complete original HTTP response.
                if period_end_start is not None or period_end_end is not None:
                    try:
                        observed_end = date.fromisoformat(item["end"])
                    except (KeyError, TypeError, ValueError):
                        rejected.append(
                            {
                                "accession": item.get("accn"),
                                "reason": "invalid_fact_period",
                                "metric": metric,
                            }
                        )
                        continue
                    if (
                        period_end_start is not None and observed_end < period_end_start
                    ) or (period_end_end is not None and observed_end > period_end_end):
                        continue
                filing = by_accession.get(item.get("accn"))
                if not filing:
                    rejected.append(
                        {
                            "accession": item.get("accn"),
                            "reason": "missing_accepted_filing",
                        }
                    )
                    continue
                try:
                    end = date.fromisoformat(item["end"])
                    start = (
                        date.fromisoformat(item["start"]) if kind == "duration" else end
                    )
                    value = Decimal(str(item["val"]))
                    if (
                        not value.is_finite()
                        or start > end
                        or end > filing["filed_date"]
                    ):
                        raise ValueError("Invalid fact")
                    key = content_hash(
                        [
                            cik,
                            item["accn"],
                            metric,
                            tag,
                            str(start),
                            str(end),
                            unit,
                            METRIC_VERSIONS[metric],
                        ]
                    )
                    if key in seen:
                        if seen[key] != value:
                            rejected.append(
                                {
                                    "accession": item["accn"],
                                    "reason": "conflicting_fact_context",
                                    "metric": metric,
                                }
                            )
                        continue
                    seen[key] = value
                    facts.append(
                        {
                            "fact_id": str(uuid.uuid5(uuid.NAMESPACE_URL, key)),
                            "cik": cik,
                            "metric_id": metric,
                            "filing_id": filing["filing_id"],
                            "source_tag": tag,
                            "tag_priority": priority,
                            "period_start": start,
                            "period_end": end,
                            "reported_fy": item.get("fy"),
                            "reported_fp": item.get("fp"),
                            "value": value,
                            "unit": unit,
                            "published_at": filing["accepted_at"],
                            "available_at": filing["available_at"],
                            "definition_version": METRIC_VERSIONS[metric],
                            "source_key": key,
                            "raw_id": raw_id,
                        }
                    )
                except (KeyError, ValueError, TypeError, ArithmeticError):
                    rejected.append(
                        {
                            "accession": item.get("accn"),
                            "reason": "invalid_fact_schema",
                            "metric": metric,
                        }
                    )
    return facts, rejected


def collect_sec(
    store,
    archive,
    client,
    cik,
    code_sha,
    delay_seconds=60,
    *,
    period_end_start=None,
    period_end_end=None,
):
    cik = cik_string(cik)
    if (
        period_end_start is not None
        and period_end_end is not None
        and period_end_start > period_end_end
    ):
        raise ValueError("Invalid SEC period range")
    with store.lock("source_http", "sec") as source_lock:
        if not source_lock:
            return {"cik": cik, "status": "locked"}
        with store.lock("sec_companyfacts", cik) as acquired:
            if not acquired:
                return {"cik": cik, "status": "locked"}
            run = store.start(
                "sec_companyfacts",
                cik,
                None,
                code_sha,
                {
                    "mapping": MAPPING_VERSION,
                    "availability": "accepted_plus_delay_v1",
                    "delay_seconds": delay_seconds,
                    "period_end_start": str(period_end_start)
                    if period_end_start
                    else None,
                    "period_end_end": str(period_end_end) if period_end_end else None,
                },
            )
            last_raw = None

            def record(payload, metadata):
                nonlocal last_raw
                checksum, uri = archive.put(payload)
                last_raw = store.raw(
                    run,
                    checksum,
                    uri,
                    datetime.now(timezone.utc),
                    metadata,
                    source="sec",
                    format="http-response-json-v1",
                )
                return last_raw

            try:
                now = datetime.now(timezone.utc)
                submissions, raw_id = client.get(
                    f"submissions/CIK{cik}.json",
                    record,
                    lambda n: store.heartbeat(run, n),
                )
                if cik_string(submissions["cik"]) != cik:
                    raise ValueError("Submissions CIK mismatch")
                filings, rejected = parse_filings(
                    submissions, cik, raw_id, now, delay_seconds
                )
                for history in submissions["filings"].get("files", []):
                    path = history["name"]
                    if not re.fullmatch(r"CIK" + cik + r"-submissions-\d+\.json", path):
                        raise ValueError("Unexpected SEC history filename")
                    older, old_raw = client.get(
                        "submissions/" + path, record, lambda n: store.heartbeat(run, n)
                    )
                    more, errors = parse_filings(
                        older, cik, old_raw, now, delay_seconds
                    )
                    filings.extend(more)
                    rejected.extend(errors)
                payload, facts_raw = client.get(
                    f"api/xbrl/companyfacts/CIK{cik}.json",
                    record,
                    lambda n: store.heartbeat(run, n),
                )
                from AI.orchestration.sec_feed import recovered_filings

                known = {f["accession_number"] for f in filings}
                filings.extend(
                    f
                    for f in recovered_filings(store, cik, archive.root)
                    if f["accession_number"] not in known
                )
                facts, errors = parse_facts(
                    payload,
                    cik,
                    filings,
                    facts_raw,
                    period_end_start=period_end_start,
                    period_end_end=period_end_end,
                )
                rejected.extend(errors)
                if not facts or rejected:
                    store.quarantine(
                        run,
                        last_raw,
                        rejected or [{"reason": "empty_facts"}],
                        len(facts),
                    )
                    return {
                        "cik": cik,
                        "run_id": run,
                        "status": "quarantined",
                        "facts": len(facts),
                        "rejected": len(rejected),
                    }
                count = store.commit_sec(cik, filings, facts, run)
                return {
                    "cik": cik,
                    "run_id": run,
                    "status": "success",
                    "facts": len(facts),
                    "inserted": count,
                }
            except (ValueError, KeyError, TypeError, ArithmeticError):
                store.quarantine(
                    run,
                    last_raw,
                    [{"reason": "invalid_sec_schema_or_conflicting_history"}],
                    0,
                )
                return {"cik": cik, "run_id": run, "status": "quarantined"}
            except Exception as exc:
                store.finish(run, "failed", type(exc).__name__)
                LOG.error("SEC run %s failed (%s)", run, type(exc).__name__)
                return {
                    "cik": cik,
                    "run_id": run,
                    "status": "failed",
                    "error_class": type(exc).__name__,
                }
