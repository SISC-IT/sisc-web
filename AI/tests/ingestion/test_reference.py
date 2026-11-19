import uuid

import pytest

from AI.orchestration.reference import normalize_reference


def record():
    return {
        "instrument_id": str(uuid.uuid4()),
        "kind": "bar",
        "source": "fixture",
        "source_key": "bar",
        "published_at": "2025-01-02T21:01:00Z",
        "available_at": "2025-01-02T21:01:00Z",
        "valid_from": "2025-01-02",
        "payload": {
            "open": "10",
            "high": "10",
            "low": "10",
            "close": "10",
            "volume": "10",
            "price_basis": "raw",
            "currency": "USD",
            "session_close": "2025-01-02T21:00:00Z",
        },
    }


def test_reference_availability_before_close_rejected():
    r = record()
    r["payload"]["session_close"] = "2025-01-02T22:00:00Z"
    with pytest.raises(ValueError):
        normalize_reference({"records": [r]})


def test_vendor_adjusted_price_cannot_be_imported_as_raw():
    r = record()
    r["payload"]["price_basis"] = "vendor_adjusted"
    with pytest.raises(ValueError):
        normalize_reference({"records": [r]})


def test_future_reference_and_zero_split_rejected():
    r = record()
    r["available_at"] = "2999-01-01T00:00:00Z"
    with pytest.raises(ValueError):
        normalize_reference({"records": [r]})
    r = record()
    r.update(
        kind="corporate_action",
        payload={
            "action_type": "split",
            "ratio_numerator": "2",
            "ratio_denominator": "0",
        },
    )
    with pytest.raises(ValueError):
        normalize_reference({"records": [r]})
