"""Portable, verifiable query snapshots. Reading never needs the source database."""

import hashlib
import json
import re
import uuid
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path

from AI.orchestration.consistent import consistent_read
from AI.orchestration.core import canonical_bytes, content_hash
from AI.orchestration.pit_store import AsOfDataset
from AI.orchestration.sec import utc

FORMAT = "sisc_snapshot_v1"


def normalize(value):
    if isinstance(value, dict):
        return {str(k): normalize(v) for k, v in value.items()}
    if isinstance(value, (list, tuple)):
        return [normalize(v) for v in value]
    if isinstance(value, (datetime, date)):
        return value.isoformat()
    if isinstance(value, (Decimal, uuid.UUID)):
        return str(value)
    return value


def source_tree_hash():
    root = Path(__file__).parent
    files = (
        sorted(root.glob("*.py"))
        + sorted(root.glob("*.json"))
        + sorted((root.parent / "migrations").glob("*.sql"))
    )
    return content_hash(
        {
            str(p.relative_to(root.parent)).replace("\\", "/"): hashlib.sha256(
                p.read_bytes()
            ).hexdigest()
            for p in files
        }
    )


def validate_spec(spec, *, now=None):
    if set(spec) - {"data_as_of", "queries", "labels"} or not spec.get("queries"):
        raise ValueError("Snapshot requires queries and an explicit data_as_of")
    knowledge = utc(spec["data_as_of"])
    if knowledge > (now if now is not None else datetime.now(timezone.utc)):
        raise ValueError("Snapshot data_as_of cannot be in the future")
    names = set()
    for query in spec["queries"]:
        if set(query) - {"name", "kind", "args", "allow_empty"}:
            raise ValueError("Unknown snapshot query field")
        name = query["name"]
        if not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", name) or name in names:
            raise ValueError("Query names must be unique safe identifiers")
        names.add(name)
        if query["kind"] not in {
            "series",
            "fundamentals",
            "bars",
            "universe",
            "references",
        }:
            raise ValueError("Unsupported snapshot dataset")
        if "data_as_of" in query["args"] or utc(query["args"]["as_of"]) > knowledge:
            raise ValueError("Query as_of must not exceed snapshot data_as_of")
        if "allow_empty" in query and not isinstance(query["allow_empty"], bool):
            raise ValueError("allow_empty must be boolean")
    canonical_bytes(spec)
    return knowledge


def materialize(store, spec):
    with consistent_read(store) as view:
        # ingested_at is assigned by PostgreSQL. Validate its cutoff against
        # that clock too, rather than the workstation's potentially skewed clock.
        server_now = view.query("SELECT clock_timestamp() AS observed_at")[0][
            "observed_at"
        ]
        knowledge = validate_spec(spec, now=server_now)
        sdk = AsOfDataset(view, data_as_of=knowledge)
        versions = view.query(
            "SELECT version FROM ingestion.schema_version ORDER BY version"
        )
        result, raw_ids, record_ids, filing_ids = {}, set(), set(), set()
        for query in spec["queries"]:
            if query["kind"] == "series":
                rows = view.series(**query["args"], data_as_of=knowledge)
            else:
                rows = getattr(sdk, query["kind"])(**query["args"])
            if not rows and not query.get("allow_empty", False):
                raise ValueError(
                    "Empty snapshot query; explicitly allow_empty if intended"
                )
            result[query["name"]] = normalize(rows)
            provenance = rows
            if query["kind"] == "universe":
                provenance = [
                    r
                    for r in sdk.references("membership", as_of=query["args"]["as_of"])
                    if r["payload"]["universe_id"] == query["args"]["universe_id"]
                ]
            for row in provenance:
                if isinstance(row, dict):
                    if row.get("raw_id"):
                        raw_ids.add(str(row["raw_id"]))
                    if row.get("filing_id"):
                        filing_ids.add(str(row["filing_id"]))
                    record_ids.update(row.get("source_record_ids", []))
        if record_ids:
            raw_ids.update(
                str(r["raw_id"])
                for r in view.query(
                    "SELECT raw_id FROM ingestion.reference_record WHERE record_id=ANY(%s::uuid[])",
                    (list(record_ids),),
                )
            )
        if filing_ids:
            raw_ids.update(
                str(r["raw_id"])
                for r in view.query(
                    "SELECT raw_id FROM ingestion.filing WHERE filing_id=ANY(%s::uuid[])",
                    (list(filing_ids),),
                )
            )
        lineage = view.query(
            """SELECT raw_id,run_id,checksum,source,format,fetched_at
            FROM ingestion.raw_object WHERE raw_id=ANY(%s::uuid[]) ORDER BY raw_id""",
            (list(raw_ids),),
        )
    return result, [v["version"] for v in versions], normalize(lineage)


def export_snapshot(store, spec, output, snapshot_id, code_sha):
    import pyarrow as pa
    import pyarrow.parquet as pq

    if not re.fullmatch(r"[A-Za-z0-9_-]{1,100}", snapshot_id):
        raise ValueError("Invalid snapshot ID")
    rows, versions, lineage = materialize(store, spec)
    identity = {
        "format": FORMAT,
        "spec": spec,
        "schema_versions": versions,
        "code_sha": code_sha,
        "source_tree_hash": source_tree_hash(),
        "source_lineage": lineage,
        "data": rows,
    }
    digest = content_hash(identity)
    target = Path(output).resolve() / snapshot_id
    # Exclusive directory reservation prevents all cooperating writers from
    # replacing a published ID. Failed/incomplete reservations remain visible.
    target.mkdir(parents=True, exist_ok=False)
    data = canonical_bytes(rows)
    with (target / "data.json").open("xb") as handle:
        handle.write(data)
    records = [
        {
            "query_name": name,
            "row_index": i,
            "payload_json": canonical_bytes(row).decode("utf-8"),
        }
        for name, batch in rows.items()
        for i, row in enumerate(batch)
    ]
    schema = pa.schema(
        [
            ("query_name", pa.string()),
            ("row_index", pa.int64()),
            ("payload_json", pa.string()),
        ]
    )
    with (target / "data.parquet").open("xb") as handle:
        pq.write_table(
            pa.Table.from_pylist(records, schema=schema),
            handle,
            compression="zstd",
            version="2.6",
        )
    manifest = {k: v for k, v in identity.items() if k != "data"}
    manifest.update(
        snapshot_id=snapshot_id,
        created_at=datetime.now(timezone.utc).isoformat(),
        content_hash=digest,
        row_counts={k: len(v) for k, v in rows.items()},
        files={
            name: hashlib.sha256((target / name).read_bytes()).hexdigest()
            for name in ("data.json", "data.parquet")
        },
    )
    # Manifest is the publish marker and is written only after both files exist.
    with (target / "manifest.json").open("xb") as handle:
        handle.write(canonical_bytes(manifest))
    register_snapshot(store, target)
    return {
        "directory": str(target),
        "content_hash": digest,
        "row_counts": manifest["row_counts"],
    }


def verify_snapshot(directory, *, expected_hash=None):
    import pyarrow.parquet as pq

    root = Path(directory).resolve()
    if any(
        (root / name).is_symlink()
        for name in ("manifest.json", "data.json", "data.parquet")
    ):
        raise ValueError("Snapshot files cannot be symlinks")
    manifest = json.loads((root / "manifest.json").read_bytes())
    if manifest["format"] != FORMAT or set(manifest["files"]) != {
        "data.json",
        "data.parquet",
    }:
        raise ValueError("Unsupported snapshot manifest")
    for name, digest in manifest["files"].items():
        if hashlib.sha256((root / name).read_bytes()).hexdigest() != digest:
            raise ValueError("Snapshot file checksum mismatch")
    rows = json.loads((root / "data.json").read_bytes())
    if {k: len(v) for k, v in rows.items()} != manifest["row_counts"]:
        raise ValueError("Snapshot row counts differ")
    identity = {
        k: manifest[k]
        for k in (
            "format",
            "spec",
            "schema_versions",
            "code_sha",
            "source_tree_hash",
            "source_lineage",
        )
    }
    digest = content_hash({**identity, "data": rows})
    if digest != manifest["content_hash"] or (
        expected_hash is not None and digest != expected_hash
    ):
        raise ValueError("Snapshot content identity mismatch")
    recovered = {name: [] for name in rows}
    for row in pq.read_table(root / "data.parquet").to_pylist():
        name = row["query_name"]
        if name not in recovered or row["row_index"] != len(recovered[name]):
            raise ValueError("Invalid Parquet row order")
        recovered[name].append(json.loads(row["payload_json"]))
    if recovered != rows:
        raise ValueError("Parquet and JSON data disagree")
    return manifest, rows


def register_snapshot(store, directory):
    manifest, _ = verify_snapshot(directory)
    root = Path(directory).resolve()
    digest = hashlib.sha256((root / "manifest.json").read_bytes()).hexdigest()
    with store.transaction() as cur:
        cur.execute(
            """INSERT INTO ingestion.dataset_snapshot(snapshot_id,content_hash,manifest_hash,directory)
                    VALUES (%s,%s,%s,%s) ON CONFLICT DO NOTHING""",
            (manifest["snapshot_id"], manifest["content_hash"], digest, str(root)),
        )
        cur.execute(
            "SELECT content_hash,manifest_hash,directory FROM ingestion.dataset_snapshot WHERE snapshot_id=%s",
            (manifest["snapshot_id"],),
        )
        if cur.fetchone() != (manifest["content_hash"], digest, str(root)):
            raise ValueError(
                "Snapshot ID already registered to different content or location"
            )
    return manifest["snapshot_id"]


def baseline(directory, query_name, *, expected_hash=None):
    """Single-asset buy-and-hold using ex-date-close reinvested total return."""
    manifest, data = verify_snapshot(directory, expected_hash=expected_hash)
    query = next(q for q in manifest["spec"]["queries"] if q["name"] == query_name)
    if query["kind"] != "bars" or query["args"]["basis"] != "total_return":
        raise ValueError("Baseline requires total_return bars")
    rows = data[query_name]
    if len(rows) < 2:
        raise ValueError("Baseline requires at least two sessions")
    values = [Decimal(r["total_return_index"]) for r in rows]
    result = {
        "strategy": "single_asset_buy_hold_v1",
        "snapshot_hash": manifest["content_hash"],
        "query": query_name,
        "first_session": rows[0]["session_date"],
        "last_session": rows[-1]["session_date"],
        "sessions": len(rows),
        "total_return": str(values[-1] / values[0] - 1),
    }
    return {**result, "result_hash": content_hash(result)}
