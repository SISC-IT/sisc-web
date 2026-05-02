# AI-359-048 Kaggle 복붙 셀

아래 셀을 Kaggle notebook에 순서대로 복붙해서 실행한다.

전제:

- Kaggle dataset에 `sisc-ai-trading-dataset`을 연결한다.
- GPU를 켠다.
- 브랜치 `feat/SISC-359-AI-signal-evaluation`에 `AI/modules/signal/models/oos2024_kaggle_train_eval.py`가 올라가 있어야 한다.
- 출력은 `/kaggle/working/oos2024`와 `/kaggle/working/oos2024_kaggle_outputs.zip`만 사용한다.

## 셀 1. 브랜치 코드 가져오기

```python
%cd /kaggle/working

BRANCH = "feat/SISC-359-AI-signal-evaluation"

!rm -rf sisc-web
!git clone -b {BRANCH} https://github.com/SISC-IT/sisc-web.git sisc-web

import sys
sys.path.insert(0, "/kaggle/working/sisc-web")
print("Path added:", sys.path[0])
```

## 셀 2. 데이터 경로와 split 설정

```python
import glob
import os
from pathlib import Path

price_files = glob.glob("/kaggle/input/**/price_data.parquet", recursive=True)
macro_files = glob.glob("/kaggle/input/**/macroeconomic_indicators.parquet", recursive=True)
stock_info_files = glob.glob("/kaggle/input/**/stock_info.parquet", recursive=True)

print("price_files:", price_files)
print("macro_files:", macro_files)
print("stock_info_files:", stock_info_files)

assert price_files, "price_data.parquet 못 찾음"
assert macro_files, "macroeconomic_indicators.parquet 못 찾음"
assert stock_info_files, "stock_info.parquet 못 찾음"

parquet_dir = str(Path(price_files[0]).parent)
os.environ["PARQUET_DIR"] = parquet_dir
os.environ["OOS2024_OUTPUT_ROOT"] = "/kaggle/working/oos2024"

os.environ["TRAIN_START_DATE"] = "2021-01-01"
os.environ["TRAIN_CUTOFF_DATE"] = "2024-06-30"
os.environ["VALIDATION_START_DATE"] = "2024-01-02"
os.environ["EVAL_START_DATE"] = "2024-09-03"
os.environ["EVAL_END_DATE"] = "2024-12-31"
os.environ["HOLDOUT_START_DATE"] = "2025-01-01"

os.environ.setdefault("SEQ_LEN", "60")
os.environ.setdefault("BATCH_SIZE", "32")
os.environ.setdefault("EPOCHS", "30")
os.environ.setdefault("TOP_K", "2")
os.environ.setdefault("BUY_THRESHOLD", "0.5")
os.environ.setdefault("CONFIDENCE_THRESHOLD", "0.0")
os.environ.setdefault("CONFIDENCE_THRESHOLD_ALT", "0.2")
os.environ.setdefault("COST_BPS_PER_SIDE", "5.0")

print("PARQUET_DIR=", os.environ["PARQUET_DIR"])
print("OOS2024_OUTPUT_ROOT=", os.environ["OOS2024_OUTPUT_ROOT"])
```

## 셀 3. config smoke 확인

```python
import json

from AI.modules.signal.models.oos2024_kaggle_train_eval import (
    build_oos2024_config,
    smoke_config_summary,
)

config = build_oos2024_config()
summary = smoke_config_summary(config)
print(json.dumps(summary, ensure_ascii=False, indent=2))

assert summary["train_cutoff"] == "2024-06-30"
assert summary["eval_start"] >= "2024-09-03"
assert summary["holdout_start"] == "2025-01-01"
assert summary["prod_artifact_overwrite"] is False
assert summary["transformer_output_dir"].endswith("/oos2024/transformer")
assert summary["itransformer_output_dir"].endswith("/oos2024/itransformer")
```

## 셀 4. 학습과 평가 실행

```python
import json

from AI.modules.signal.models.oos2024_kaggle_train_eval import run_oos2024_kaggle_train_eval

result = run_oos2024_kaggle_train_eval(config)
print(json.dumps(result, ensure_ascii=False, indent=2))
```

## 셀 5. 필수 output 확인

```python
from pathlib import Path

expected_outputs = [
    "/kaggle/working/oos2024/transformer/multi_horizon_model.keras",
    "/kaggle/working/oos2024/transformer/multi_horizon_scaler.pkl",
    "/kaggle/working/oos2024/transformer/metadata.json",
    "/kaggle/working/oos2024/transformer/transformer_signal_frame.csv",
    "/kaggle/working/oos2024/transformer/transformer_diagnostics_frame.csv",
    "/kaggle/working/oos2024/transformer/transformer_metric_frame.csv",
    "/kaggle/working/oos2024/transformer/transformer_objective_frame.csv",
    "/kaggle/working/oos2024/transformer/transformer_leaderboard_frame.csv",
    "/kaggle/working/oos2024/transformer/transformer_eval_summary.json",
    "/kaggle/working/oos2024/itransformer/multi_horizon_model.keras",
    "/kaggle/working/oos2024/itransformer/multi_horizon_scaler.pkl",
    "/kaggle/working/oos2024/itransformer/metadata.json",
    "/kaggle/working/oos2024/itransformer/itransformer_signal_frame.csv",
    "/kaggle/working/oos2024/itransformer/itransformer_diagnostics_frame.csv",
    "/kaggle/working/oos2024/itransformer/itransformer_metric_frame.csv",
    "/kaggle/working/oos2024/itransformer/itransformer_objective_frame.csv",
    "/kaggle/working/oos2024/itransformer/itransformer_leaderboard_frame.csv",
    "/kaggle/working/oos2024/itransformer/itransformer_eval_summary.json",
    "/kaggle/working/oos2024/oos2024_kaggle_train_summary.json",
    "/kaggle/working/oos2024/oos2024_combined_signal_frame.csv",
    "/kaggle/working/oos2024/oos2024_combined_leaderboard_frame.csv",
    "/kaggle/working/oos2024/oos2024_confidence_0_2_summary.csv",
    "/kaggle/working/oos2024/oos2024_universe_excess_summary.csv",
]

for raw_path in expected_outputs:
    path = Path(raw_path)
    print(raw_path, "OK" if path.exists() else "MISSING", path.stat().st_size if path.exists() else "")

missing = [raw_path for raw_path in expected_outputs if not Path(raw_path).exists()]
assert not missing, f"누락 output: {missing}"
```

## 셀 6. 결과 전체 zip 만들기

```python
import shutil
from pathlib import Path

output_dir = Path("/kaggle/working/oos2024")
zip_base = Path("/kaggle/working/oos2024_kaggle_outputs")

assert output_dir.exists(), f"결과 폴더 없음: {output_dir}"
zip_path = shutil.make_archive(
    str(zip_base),
    "zip",
    root_dir=str(output_dir.parent),
    base_dir=output_dir.name,
)

zip_file = Path(zip_path)
print("ZIP=", zip_file)
print("SIZE=", zip_file.stat().st_size)
assert zip_file.exists() and zip_file.stat().st_size > 0
```

## Kaggle에서 받을 파일

아래 파일 하나를 받으면 된다.

```text
/kaggle/working/oos2024_kaggle_outputs.zip
```
