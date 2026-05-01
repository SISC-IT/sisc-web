# iTransformer Kaggle 학습 + 평가 실행 안내

## 목적

`itransformer_regime_corr_v1` 모델을 Kaggle GPU에서 풀 학습한 뒤, 같은 실행에서 risk/regime gate 후보 평가 산출물까지 생성한다. 로컬에서는 학습, sweep, Kaggle push를 실행하지 않는다.

## Kaggle 입력 데이터셋

기본 데이터셋 경로는 `/kaggle/input/sisc-ai-trading-dataset`이다.

필수 파일:

- `price_data.parquet`
- `macroeconomic_indicators.parquet`
- `sisc_ai_code.zip`

`sisc_ai_code.zip`은 `AI/scripts/upload_to_kaggle.py`가 데이터셋 버전에 포함하는 코드 아카이브다.

## 기본 기간 정책

- 학습 기간: `2021-01-01..2024-12-31`
- 평가 요청 기간: `2024-10-01..2024-12-31`
- label cutoff: `2024-12-31`
- holdout 시작: `2025-01-01`

평가 row는 모든 horizon의 `label_end_date`가 `label_cutoff_date` 이내인 경우만 사용한다. 따라서 요청 평가 종료일이 `2024-12-31`이어도, 7일 horizon label이 cutoff를 넘는 asof row는 평가에서 제외된다.

## Kaggle 노트북 실행 순서

1. Kaggle Notebook에 `AI/notebooks/itransformer_kaggle_train_eval.ipynb`를 업로드한다.
2. 입력 데이터셋으로 `jihyeongkimm/sisc-ai-trading-dataset`을 붙인다.
3. Accelerator를 GPU로 설정한다.
4. Internet은 끈 상태로 둔다.
5. 첫 셀부터 순서대로 실행한다.
6. 마지막 검증 셀에서 `/kaggle/working` 산출물 크기를 확인한다.

## 최종 실행 엔트리포인트

노트북 내부 최종 실행 코드는 아래 한 줄이다.

```python
from AI.modules.signal.models.itransformer.kaggle_train_eval import run_kaggle_train_eval
result = run_kaggle_train_eval()
```

기존 PatchTST 노트북처럼 `/kaggle/working/sisc-web`를 직접 쓰는 경우에는 아래 방식도 가능하다.

```python
import importlib.util
import sys
from pathlib import Path

sys.path.insert(0, "/kaggle/working/sisc-web")
script_path = Path("/kaggle/working/sisc-web/AI/modules/signal/models/itransformer/kaggle_train_eval.py")
spec = importlib.util.spec_from_file_location("itransformer_kaggle_train_eval", script_path)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)
module.run_kaggle_train_eval()
```

자동 커널 생성 경로에서는 `AI/scripts/trigger_training.py --dry-run --no-wait`가 iTransformer에 대해 같은 엔트리포인트를 생성한다.

## 학습 artifact

| Kaggle output | download_weights.py 저장 위치 | wrapper 계약 |
| --- | --- | --- |
| `/kaggle/working/multi_horizon_model.keras` | `AI/data/weights/itransformer/multi_horizon_model.keras` | `artifact_paths.py`의 iTransformer model path |
| `/kaggle/working/multi_horizon_scaler.pkl` | `AI/data/weights/itransformer/multi_horizon_scaler.pkl` | wrapper `scaler_path` |
| `/kaggle/working/metadata.json` | `AI/data/weights/itransformer/metadata.json` | wrapper metadata sidecar |

`metadata.json`에는 `model_name`, `feature_set_ver`, `feature_columns`, `feature_count`, `seq_len`, `horizons`, `scaler_path`, `model_path`, `architecture_config`, `train_window`, `validation_window`, `label_cutoff_date`가 포함된다.

## 평가 artifact

기본 `confidence_threshold=0.0` 평가:

- `itransformer_signal_frame.csv`
- `itransformer_diagnostics_frame.csv`
- `itransformer_metric_frame.csv`
- `itransformer_objective_frame.csv`
- `itransformer_leaderboard_frame.csv`
- `itransformer_eval_summary.json`

추가 `confidence_threshold=0.2` 요약 평가:

- `itransformer_conf020_diagnostics_frame.csv`
- `itransformer_conf020_metric_frame.csv`
- `itransformer_conf020_objective_frame.csv`
- `itransformer_conf020_leaderboard_frame.csv`

## 평가 계약

- horizon은 `[1, 3, 5, 7]`만 사용한다.
- `prediction_status="ok"` row만 백테스트에 사용한다.
- fallback/error/legacy row는 diagnostics에 남긴다.
- `missing_return_policy="error"`를 사용한다.
- universe_equal baseline을 함께 생성한다.
- iTransformer objective profile은 `net_return`, `MDD`, `Calmar`, `downside_return`, universe_equal 대비 성과를 확인하는 risk/regime gate 후보 평가에 맞춘다.

## 실패 시 확인할 체크리스트

- Kaggle 입력 데이터셋에 `price_data.parquet`, `macroeconomic_indicators.parquet`, `sisc_ai_code.zip`이 모두 있는가?
- Notebook의 `PARQUET_DIR`가 실제 데이터셋 mount 경로와 같은가?
- `TRAIN_END_DATE`가 `2025-01-01`보다 이전인가?
- `LABEL_CUTOFF_DATE`가 `TRAIN_END_DATE`를 넘지 않는가?
- `metadata.json`의 `feature_count`와 `feature_columns` 길이가 같은가?
- `itransformer_signal_frame.csv`에 `prediction_status="ok"` row가 존재하는가?
- diagnostics에서 `missing_return_rate`가 0인가?
- Kaggle output에 필수 학습 artifact 3개와 평가 CSV/JSON이 모두 생성되었는가?
