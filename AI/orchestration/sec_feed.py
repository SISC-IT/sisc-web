"""Recover date-only filings from original SEC daily feed members, without invented times."""

import hashlib
import re
import uuid
from datetime import datetime, time, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo


def parse_feed_submission(payload, source_url, member, raw_id=None):
    from AI.orchestration.sec import FORMS, cik_string

    match = re.fullmatch(
        r"https://www\.sec\.gov/Archives/edgar/Feed/(\d{4})/QTR([1-4])/(\d{8})\.nc\.tar\.gz",
        source_url,
    )
    if not match:
        raise ValueError("Official SEC daily-feed URL required")
    # Read only the SGML header and first document header, never body text.
    header, separator, document = payload.partition(b"<DOCUMENT>")
    if not separator or len(header) > 100000:
        raise ValueError("Invalid feed submission header")

    def field(data, name):
        values = re.findall(rb"(?m)^<" + name.encode() + rb">([^\r\n<]+)", data)
        if len(values) != 1:
            raise ValueError("Missing or ambiguous feed identity")
        return values[0].decode("ascii").strip()

    accession = field(header, "ACCESSION-NUMBER")
    if not re.fullmatch(r"\d{10}-\d{2}-\d{6}", accession) or member not in {
        accession + ".nc",
        "./" + accession + ".nc",
    }:
        raise ValueError("Feed member accession mismatch")
    filed = datetime.strptime(field(header, "FILING-DATE"), "%Y%m%d").date()
    feed_day = datetime.strptime(match[3], "%Y%m%d").date()
    if (
        filed != feed_day
        or str(feed_day.year) != match[1]
        or (feed_day.month - 1) // 3 + 1 != int(match[2])
    ):
        raise ValueError("Feed date identity mismatch")
    form = field(header, "TYPE")
    if form not in FORMS:
        raise ValueError("Unsupported feed form")
    available = datetime.combine(
        filed + timedelta(days=1), time(), ZoneInfo("America/New_York")
    ).astimezone(timezone.utc) + timedelta(seconds=60)
    if available > datetime.now(timezone.utc):
        raise ValueError("Future daily feed")
    return {
        "filing_id": str(uuid.uuid5(uuid.NAMESPACE_URL, "sec:" + accession)),
        "cik": cik_string(field(header, "CIK")),
        "accession_number": accession,
        "form_type": form,
        "period_end": datetime.strptime(field(header, "PERIOD"), "%Y%m%d").date(),
        "filed_date": filed,
        "accepted_at": None,
        "available_at": available,
        "availability_basis": "sec_feed_day_end",
        "is_amendment": form.endswith("/A"),
        "primary_document": field(document.split(b"<TEXT>", 1)[0], "FILENAME"),
        "raw_id": raw_id,
    }


def register_feed_submission(store, archive, payload, source_url, member, code_sha):
    filing = parse_feed_submission(payload, source_url, member)
    cik = filing["cik"]
    with store.lock("sec_companyfacts", cik) as acquired:
        if not acquired:
            return {"status": "locked"}
        run = store.start(
            "sec_feed_recovery",
            cik,
            None,
            code_sha,
            {"availability": "sec_feed_day_end"},
        )
        try:
            checksum, uri = archive.put(payload)
            filing["raw_id"] = store.raw(
                run,
                checksum,
                uri,
                datetime.now(timezone.utc),
                {"url": source_url, "member": member},
                source="sec",
                format="sec-feed-submission-v1",
            )
            store.commit_sec(cik, [filing], [], run, advance_watermark=False)
            return {
                "status": "success",
                "run_id": run,
                "accession": filing["accession_number"],
                "available_at": filing["available_at"].isoformat(),
            }
        except Exception as exc:
            store.finish(run, "failed", type(exc).__name__)
            raise


def recovered_filings(store, cik, archive_root):
    rows = store.query(
        """SELECT f.*,r.object_uri,r.checksum,r.request_metadata FROM ingestion.filing f
        JOIN ingestion.raw_object r USING(raw_id) WHERE f.cik=%s AND r.format='sec-feed-submission-v1'""",
        (cik,),
    )
    result = []
    for row in rows:
        path = Path(row["object_uri"]).resolve()
        if not path.is_relative_to(Path(archive_root).resolve()):
            raise ValueError("Feed evidence outside archive")
        payload = path.read_bytes()
        if hashlib.sha256(payload).hexdigest() != row["checksum"]:
            raise ValueError("Feed evidence checksum mismatch")
        result.append(
            parse_feed_submission(
                payload,
                row["request_metadata"]["url"],
                row["request_metadata"]["member"],
                row["raw_id"],
            )
        )
    return result
