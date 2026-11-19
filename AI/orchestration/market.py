"""Daily Yahoo SDK adapter. SDK output is archived before normalization.

This archive is explicitly NOT the original Yahoo HTTP response. Historical vendor
adjustments and publication timestamps cannot be reconstructed by this adapter.
"""

import json
from datetime import timedelta


class YahooDaily:
    def fetch(self, ticker, span):
        import yfinance as yf

        frame = yf.Ticker(ticker).history(
            start=span.start.isoformat(),
            end=(span.end + timedelta(days=1)).isoformat(),
            interval="1d",
            auto_adjust=False,
            back_adjust=False,
            actions=True,
            raise_errors=True,
            timeout=30,
        )
        # Capture SDK column names, index and data before deriving anything.
        return frame.to_json(orient="split", date_format="iso").encode("utf-8")

    def parse(self, payload):
        data = json.loads(payload)
        if len(data["index"]) != len(data["data"]):
            raise ValueError("SDK index/data length mismatch")
        fields = dict(zip(data["columns"], range(len(data["columns"]))))
        rows = []
        for timestamp, values in zip(data["index"], data["data"]):
            if len(values) != len(data["columns"]):
                raise ValueError("SDK column/data length mismatch")
            row = {"date": timestamp[:10]}
            for source, target in [
                ("Open", "open"),
                ("High", "high"),
                ("Low", "low"),
                ("Close", "close"),
                ("Volume", "volume"),
                ("Adj Close", "adjusted_close"),
            ]:
                value = values[fields[source]] if source in fields else None
                row[target] = None if value is None else str(value)
            rows.append(row)
        return rows
