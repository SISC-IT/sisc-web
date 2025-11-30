# transformer/modules/inference.py
from __future__ import annotations
from typing import Dict, List, Optional, Tuple
import numpy as np
import pandas as pd
from sklearn.preprocessing import MinMaxScaler
from tensorflow.keras import Model 

from transformer.modules.models import build_transformer_classifier
from transformer.modules.features import FEATURES, build_features

CLASS_NAMES = ["BUY", "HOLD", "SELL"]

# ===== 내부 유틸 =====
def _make_sequence(feats: pd.DataFrame, use_cols: List[str], seq_len: int) -> Optional[np.ndarray]:
    """마지막 구간(seq_len)만 잘라서 (seq_len, n_features) 배열 생성."""
    if len(feats) < seq_len:
        return None
    X = feats[use_cols].iloc[-seq_len:].copy()
    return X.values.astype("float32")

def _scale_per_ticker(seq_arr: np.ndarray) -> Tuple[np.ndarray, MinMaxScaler]:
    """
    (중요) 추론 단계에서는 학습 시 저장한 스케일러 사용이 가장 바람직.
    - 다만, '티커별 미세 스케일링' 전략을 유지하고자 할 때는 아래처럼
      입력 시퀀스에 대해 개별 MinMax를 적용할 수 있음(일관성↓, 적응성↑).
    """
    scaler = MinMaxScaler(feature_range=(0, 1), clip=True)
    X_scaled = scaler.fit_transform(seq_arr)
    return X_scaled.astype("float32"), scaler

def _load_or_build_model(seq_len: int, n_features: int, weights_path: Optional[str]) -> Model:
    """가중치 로드 전용. 가중치 경로 없으면 경고 후 랜덤 초기화(추론 품질↓)."""
    model = build_transformer_classifier(seq_len, n_features)
    if weights_path:
        try:
            model.load_weights(weights_path)
            print(f"[INFER] 가중치 로드 완료 : {weights_path}")
        except Exception as e:
            print(f"[INFER][WARN] 가중치 로드 실패 → 랜덤 초기화: {e}")
    else:
        print("[INFER][WARN] weights_path 미지정 → 랜덤 초기화로 진행")
    return model

# ===== 공개 엔트리포인트 (추론) =====
def run_inference(
    *,
    finder_df: pd.DataFrame,
    raw_data: pd.DataFrame,
    seq_len: int,
    pred_h: int,  # (현재는 미사용; 로그/정책에 남겨두기용)
    weights_path: Optional[str],
    run_date: Optional[str] = None,
    interval: str = "1d",
) -> Dict[str, pd.DataFrame]:
    """
    ※ 추론 전용 함수
    - 입력: 선정된 종목 목록(finder_df), OHLCV 원천(raw_data)
    - 처리: 피처→시퀀스→스케일링→모델 예측
    - 출력: logs DataFrame (feature_name1~3 + feature_score1~3 추가)
    """
    tickers = finder_df["ticker"].astype(str).tolist()
    if raw_data is None or raw_data.empty:
        print("[INFER] raw_data empty -> empty logs") 
        return {"logs": pd.DataFrame(columns=[
            "ticker","date","action","price","weight",
            "feature1","feature2","feature3",
            "feature_name1","feature_name2","feature_name3",
            "feature_score1","feature_score2","feature_score3",
            "prob1","prob2","prob3"
        ])}

    df = raw_data.copy()
    ts_col = "ts_local" if "ts_local" in df.columns else ("date" if "date" in df.columns else None)
    if ts_col is None:
        raise ValueError("raw_data에 'ts_local' 또는 'date' 컬럼이 필요합니다.")
    df[ts_col] = pd.to_datetime(df[ts_col])
    df = df.rename(columns={c: c.lower() for c in df.columns})
    df = df[df["ticker"].astype(str).isin(tickers)]
    if df.empty:
        print("[INFER] 대상 종목 데이터 없음")
        return {"logs": pd.DataFrame(columns=[
            "ticker","date","action","price","weight",
            "feature1","feature2","feature3",
            "feature_name1","feature_name2","feature_name3",
            "feature_score1","feature_score2","feature_score3",
            "prob1","prob2","prob3"
        ])}

    # run_date 컷
    if run_date is None:
        end_dt = pd.Timestamp.now(tz="Asia/Seoul").normalize() + pd.Timedelta(days=1) - pd.Timedelta(microseconds=1)
    else:
        end_dt = pd.to_datetime(run_date).tz_localize("Asia/Seoul", nonexistent="shift_forward").normalize()
        end_dt = end_dt + pd.Timedelta(days=1) - pd.Timedelta(microseconds=1)

    if df[ts_col].dt.tz is not None:
        end_cut = end_dt.tz_convert(df[ts_col].dt.tz)
    else:
        end_cut = end_dt.tz_localize(None)

    df = df[df[ts_col] <= end_cut].sort_values(["ticker", ts_col]).reset_index(drop=True)

    # 모델 입력 피처 목록 (CLOSE_RAW는 입력 제외)
    model_feats = [f for f in FEATURES if f != "CLOSE_RAW"]
    n_features = len(model_feats)

    # ★ 추론: 반드시 학습 가중치를 로드
    model = _load_or_build_model(seq_len=seq_len, n_features=n_features, weights_path=weights_path)

    rows: List[dict] = []
    for t, g in df.groupby("ticker", sort=False):
        try:
            if g.empty:
                continue

            g = g.rename(columns={ts_col: "date"}).set_index("date")
            ohlcv = g[["open", "high", "low", "close", "volume"]].copy()

            feats = build_features(ohlcv)
            if feats.empty:
                print(f"[INFER] {t} features empty -> skip")
                continue

            # === 모델 입력 시퀀스 생성 ===
            X_seq = _make_sequence(feats, model_feats, seq_len)
            if X_seq is None:
                print(f"[INFER] {t} 부족한 길이(seq_len={seq_len}) -> skip")
                continue

            X_scaled, _ = _scale_per_ticker(X_seq)
            X_scaled = np.expand_dims(X_scaled, axis=0)  # (1, seq_len, n_features)

            # === 모델 예측 ===
            try:
                probs = model.predict(X_scaled, verbose=0)[0]
                probs = np.clip(probs.astype(float), 1e-6, 1.0)
                probs = probs / probs.sum()
                buy_p, hold_p, sell_p = float(probs[0]), float(probs[1]), float(probs[2])
                action = ["BUY","HOLD","SELL"][int(np.argmax(probs))]
            except Exception as e:
                print(f"[INFER][WARN] 예측 실패({t}) → 룰기반 fallback: {e}")
                recent_fb = feats.iloc[-1]
                rsi = float(recent_fb.get("RSI", np.nan))
                macd = float(recent_fb.get("MACD", np.nan))
                if rsi < 30 and macd > 0:
                    action = "BUY"; buy_p, hold_p, sell_p = 0.65, 0.30, 0.05
                elif rsi > 70 and macd < 0:
                    action = "SELL"; buy_p, hold_p, sell_p = 0.05, 0.30, 0.65
                else:
                    action = "HOLD"; buy_p, hold_p, sell_p = 0.33, 0.34, 0.33

            # === 비중(가중치) 간단 정책 ===
            p_max = max(buy_p, hold_p, sell_p)
            confidence = float(np.clip((p_max - 1/3) * 1.5, 0.0, 1.0))
            ret = 0.0
            if len(feats) > 2 and "CLOSE_RAW" in feats.columns:
                c_now = float(feats["CLOSE_RAW"].iloc[-1])
                c_prev = float(feats["CLOSE_RAW"].iloc[-2])
                if c_prev:
                    ret = (c_now / c_prev) - 1.0
            weight = float(np.clip(0.05 + confidence * 0.20 + abs(ret) * 0.05, 0.05, 0.30))

            # === 최근값/가격 ===
            recent = feats.iloc[-1]
            close_price = float(recent.get("CLOSE_RAW", np.nan))

            # === 상위 3개 피처 자동 선별 (히스토리 Min–Max 점수 기준) ===
            candidate_cols = [c for c in FEATURES if c != "CLOSE_RAW"]
            eps = 1e-12
            scores = []  # (name, raw_value, norm_score)
            for c in candidate_cols:
                if c not in feats.columns:
                    continue
                series = feats[c].astype(float)
                v = float(recent[c]) if pd.notna(recent[c]) else np.nan
                if not series.notna().any():
                    score = -np.inf
                else:
                    mn = float(np.nanmin(series.values))
                    mx = float(np.nanmax(series.values))
                    if np.isnan(v) or np.isnan(mn) or np.isnan(mx) or mx - mn < eps:
                        score = -np.inf
                    else:
                        score = (v - mn) / (mx - mn + eps)  # 0~1
                scores.append((c, v, score))

            scores.sort(key=lambda x: x[2], reverse=True)
            top3 = [item for item in scores if np.isfinite(item[2])][:3]

            f1_name = top3[0][0] if len(top3) > 0 else None
            f2_name = top3[1][0] if len(top3) > 1 else None
            f3_name = top3[2][0] if len(top3) > 2 else None

            f1_val = float(top3[0][1]) if len(top3) > 0 and top3[0][1] is not None else np.nan
            f2_val = float(top3[1][1]) if len(top3) > 1 and top3[1][1] is not None else np.nan
            f3_val = float(top3[2][1]) if len(top3) > 2 and top3[2][1] is not None else np.nan

            f1_score = float(top3[0][2]) if len(top3) > 0 else np.nan
            f2_score = float(top3[1][2]) if len(top3) > 1 else np.nan
            f3_score = float(top3[2][2]) if len(top3) > 2 else np.nan

            rows.append({
                "ticker": str(t),
                "date": feats.index[-1].strftime("%Y-%m-%d"),
                "action": action,
                "price": close_price,
                "weight": weight,
                "feature1": f1_val,
                "feature2": f2_val,
                "feature3": f3_val,
                "feature_name1": f1_name,
                "feature_name2": f2_name,
                "feature_name3": f3_name,
                "feature_score1": f1_score,
                "feature_score2": f2_score,
                "feature_score3": f3_score,
                "prob1": float(buy_p),
                "prob2": float(hold_p),
                "prob3": float(sell_p),
            })
        except Exception as e:
            print(f"[INFER][ERROR] {t}: {e}")
            continue

    logs_df = pd.DataFrame(rows, columns=[
        "ticker","date","action","price","weight",
        "feature1","feature2","feature3",
        "feature_name1","feature_name2","feature_name3",
        "feature_score1","feature_score2","feature_score3",
        "prob1","prob2","prob3"
    ])
    return {"logs": logs_df}

# ===== XAI 어댑터: logs_df -> decisions(list[dict]) =====
def logs_to_xai_decisions(logs_df: pd.DataFrame) -> List[Dict[str, object]]:
    """
    XAI 쪽 generate_report_from_yf(decision, evidence, api_key) 포맷으로 변환.
    decision 예시:
    {
        "ticker": "AAPL",
        "date": "2024-12-16",
        "signal": "BUY",
        "price": 453.72,
        "evidence": [
            {"feature_name": "MA_5", "contribution": 0.0186},
            ...
        ]
    }
    """
    if logs_df is None or logs_df.empty:
        return []

    decisions: List[Dict[str, object]] = []
    for _, row in logs_df.iterrows():
        # evidence 구성 (이름 + 정규화 점수 → contribution)
        evidence = []
        for i in (1, 2, 3):
            name = row.get(f"feature_name{i}")
            score = row.get(f"feature_score{i}")  # 0~1
            if pd.isna(name):
                continue
            contrib = None if pd.isna(score) else float(score)
            evidence.append({"feature_name": str(name), "contribution": contrib})

        decisions.append({
            "ticker": row.get("ticker"),
            "date": str(row.get("date")),
            "signal": row.get("action"),  # action -> signal
            "price": float(row.get("price")) if pd.notna(row.get("price")) else None,
            "evidence": evidence
        })
    return decisions
