# transformer/main.py
from __future__ import annotations
from typing import Dict, Optional
import pandas as pd
from pathlib import Path


# (선택) 프로젝트 공용 로거가 있다면 교체: from AI.libs.utils.io import _log
_log = print

# ★ 실제 추론 로직은 modules/inference.run_inference 에 구현되어 있음
from .modules.inference import run_inference


def run_transformer(
    *,
    finder_df: pd.DataFrame,
    seq_len: int,
    pred_h: int,
    raw_data: pd.DataFrame,
    run_date: Optional[str] = None,
    config: Optional[dict] = None,
    interval: str = "1d",
) -> Dict[str, pd.DataFrame]:
    """

    Parameters
    ----------
    finder_df : pd.DataFrame
        ['ticker'] 컬럼 포함. Finder 단계에서 선정된 추론 대상 종목 목록.
    seq_len : int
        모델 입력 시퀀스 길이(예: 64).
    pred_h : int
        예측 지평(예: 5). 라벨링/정책 기준(로그, 가중치 산정 보조)에 쓰이며
        추론 확률 계산 자체에는 직접 관여하지 않음.
    raw_data : pd.DataFrame
        OHLCV 시계열. 필수 컬럼:
        ['ticker','open','high','low','close','volume', ('ts_local' or 'date')]
    run_date : Optional[str]
        'YYYY-MM-DD' 형식. 지정 시, 해당 날짜(포함)까지의 데이터만 사용해 추론.
        미지정 시, Asia/Seoul 기준 당일 종가까지 사용.
    config : Optional[dict]
        config["transformer"]["model_path"] 에 학습된 가중치 경로가 존재해야 함.
        예) {"transformer": {"model_path": "artifacts/transformer_cls.h5"}}
        (추후 추론 방식 옵션이 늘어나면 이 dict 에 플래그/파라미터를 확장하세요.)
    interval : str
        캔들 간격 표기(로그용). 예: '1d', '1h' 등.

    Returns
    -------
    Dict[str, pd.DataFrame]
        {"logs": DataFrame} 형식.
        컬럼: ["ticker","date","action","price","weight",
               "feature1","feature2","feature3","prob1","prob2","prob3"]

    Notes
    -----
    - 이 래퍼는 '이름/시그니처의 안정성' 확보가 목적입니다.
      내부 추론 엔진이 변경되어도 외부 호출부 수정 없이 교체가 가능합니다.
    """

    # 1) weights_path 경로지정
    base_dir = Path("/transformer/weights")
    candidate = base_dir / "initial.weights.h5"

    weights_path = str(candidate) if candidate.exists() else None

    if not weights_path:
        _log("[TRANSFORMER][WARN] weights_path 미설정 → 가중치 없이 랜덤 초기화로 추론될 수 있음(품질 저하).")
        _log("  config 예시: {'transformer': {'weights_path': 'weights/initial.weights.h5'}}")


    # 2) 실제 추론 실행(모듈 위임)
    return run_inference(
        finder_df=finder_df,
        raw_data=raw_data,
        seq_len=seq_len,
        pred_h=pred_h,
        weights_path=weights_path,   # ★ 학습 가중치 경로 전달
        run_date=run_date,
        interval=interval,
    )
