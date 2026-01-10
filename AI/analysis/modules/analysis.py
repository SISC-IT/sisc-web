from datetime import datetime
from typing import List, Dict, Any
from collections import defaultdict

def kdate(s: str) -> str:
    dt = datetime.strptime(s, "%Y-%m-%d")
    return f"{dt.month}월 {dt.day}일"

def chunk3(values: List[float]) -> List[List[float]]:
    n = len(values)
    s1 = n // 3
    s2 = (n - s1) // 2
    s3 = n - (s1 + s2)
    idx, parts = 0, []
    for s in (s1, s2, s3):
        parts.append(values[idx: idx + s] if s else [])
        idx += s
    return parts

def analyze_feature_series(feature: str, context_data: List[Dict[str, Any]]) -> Dict[str, float]:
    vals = [float(day[feature]) for day in context_data]
    e, m, l = chunk3(vals)
    avg = lambda x: sum(x)/len(x) if x else 0.0
    return {
        "start": vals[0],
        "end": vals[-1],
        "min": min(vals),
        "max": max(vals),
        "eavg": avg(e),
        "mavg": avg(m),
        "lavg": avg(l),
    }

def consolidate_evidence(evidence: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    agg = defaultdict(float)
    for ev in evidence:
        agg[ev["feature_name"]] += float(ev["contribution"])
    items = [{"feature_name": k, "contribution": v} for k, v in agg.items()]
    items.sort(key=lambda x: x["contribution"], reverse=True)
    return items
