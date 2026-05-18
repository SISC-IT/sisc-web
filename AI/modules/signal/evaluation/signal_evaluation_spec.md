# AI 시그널 모델 평가 설계서

지시서 번호: AI-359-001
담당: 척도
이슈: #359
목적: 4개 AI 시그널 모델을 같은 기준으로 평가하고, 이후 MoE/Gating 구조로 확장할 수 있는 평가판을 만든다.

## 1. 범위와 원칙

이번 작업의 핵심은 모델 구조를 크게 바꾸는 것이 아니라, 모델을 공정하게 비교할 수 있는 예측 저장 형식, 지표, walk-forward 백테스트, leaderboard 산출 구조를 정하는 것이다.

설계 원칙은 다음과 같다.

- 모든 모델의 예측은 같은 row 단위 스키마로 저장한다.
- 학습, 검증, 평가 기간은 모델별로 다르게 잡지 않고 같은 walk-forward fold를 사용한다.
- 스케일러, 피처 선택, threshold, 고정 가중치는 반드시 학습 구간에서만 결정한다.
- 평가 구간에서는 이미 저장된 out-of-fold 예측값만 사용해 백테스트와 leaderboard를 만든다.
- GitHub Actions 기준 자동화는 제외하고, 서버 크론잡에서 실행 가능한 구조를 기준으로 한다.
- `wandb/`는 실험 로그이므로 커밋 대상에 포함하지 않는다.

## 2. 훑어본 파일 목록

모델 구조 확인:

- `AI/modules/signal/models/transformer/architecture.py`
- `AI/modules/signal/models/transformer/train.py`
- `AI/modules/signal/models/transformer/train_kaggle.py`
- `AI/modules/signal/models/transformer/wrapper.py`
- `AI/modules/signal/models/TCN/architecture.py`
- `AI/modules/signal/models/TCN/train.py`
- `AI/modules/signal/models/TCN/train_kaggle.py`
- `AI/modules/signal/models/TCN/wrapper.py`
- `AI/modules/signal/models/PatchTST/architecture.py`
- `AI/modules/signal/models/PatchTST/train.py`
- `AI/modules/signal/models/PatchTST/train_kaggle.py`
- `AI/modules/signal/models/PatchTST/wrapper.py`
- `AI/modules/signal/models/itransformer/architecture.py`
- `AI/modules/signal/models/itransformer/train.py`
- `AI/modules/signal/models/itransformer/train_kaggle.py`
- `AI/modules/signal/models/itransformer/wrapper.py`

공통 코어와 연결 지점 확인:

- `AI/modules/signal/core/base_model.py`
- `AI/modules/signal/core/data_loader.py`
- `AI/modules/signal/core/dataset_builder.py`
- `AI/modules/signal/core/artifact_paths.py`
- `AI/modules/signal/models/__init__.py`
- `AI/pipelines/components/model_manager.py`
- `AI/pipelines/components/portfolio_logic.py`
- `test_ensemble_backtest.py`

확인 결과:

- `AI/tests/run_backtest.py`는 현재 작업트리에 없다.
- `test_ensemble_backtest.py`는 존재하지만 `.gitignore`의 `test_*.py` 규칙 때문에 커밋 대상이 아니며, 현재는 참고용 스크립트 성격이 강하다.

## 3. 현재 모델 구조 요약

### 3.1 Transformer

구조:

- TensorFlow/Keras 기반 multi-horizon classifier.
- 입력은 시계열 텐서, ticker id, sector id 3개다.
- 출력은 sigmoid 4개이며 기본 horizon은 `[1, 3, 5, 7]`이다.

학습 입력:

- `train.py`는 `DataLoader(lookback=60)`와 `TRANSFORMER_TRAIN_FEATURES`를 사용한다.
- 주요 피처는 일봉 기술 피처와 주봉/월봉 피처 17개다.
- `DataLoader.create_dataset()` 내부에서 `MinMaxScaler`를 전체 데이터에 fit한 뒤 시퀀스를 만든다.
- `train.py`는 이후 `train_test_split(..., shuffle=True)`를 사용한다.
- `train_kaggle.py`는 parquet를 읽고, ticker 기준 train/validation 분리 후 `StandardScaler`를 train에만 fit한다.

추론 입력:

- `TransformerSignalModel.load_scaler()`가 scaler의 `feature_names_in_`를 읽을 수 있으면 그 순서를 추론 피처로 복원한다.
- `feature_names_in_`가 없으면 config의 `features`나 scaler 폭으로 추정한다.
- `get_signals()`는 필요한 피처가 없으면 오류를 낸다.

wrapper 출력:

- `{"transformer_1d": float, "transformer_3d": float, "transformer_5d": float, "transformer_7d": float}`

확인 필요:

- 로컬 `train.py`는 전체 데이터 기준 scaler fit과 shuffle split이 있어 평가용 기준으로는 누수 위험이 있다.
- 로컬 `train.py`는 별도 metadata를 저장하지 않는다. 구버전 scaler에 `feature_names_in_`가 없으면 추론 피처 순서를 완전히 보장하기 어렵다.
- `DataLoader.create_dataset()`는 요청 피처 중 실제 존재하는 컬럼만 조용히 채택한다. 평가 파이프라인에서는 누락 피처를 오류로 처리해야 한다.

### 3.2 TCN

구조:

- PyTorch 기반 TCN classifier.
- 입력은 `[batch, seq_len, features]`이고 내부에서 `[batch, features, seq_len]`로 바꿔 Conv1d를 적용한다.
- 출력은 horizon별 logits이며 wrapper에서 sigmoid로 확률화한다.

학습 입력:

- 기본 `seq_len=60`, horizon은 `[1, 3, 5, 7]`.
- 학습 피처는 11개 일봉 기술 피처다.
- 로컬 `train.py`는 날짜 기준 80퍼센트 지점으로 train/validation을 나누고 `StandardScaler`를 train에만 fit한다.
- `train_kaggle.py`도 날짜 기준으로 나누며 기본적으로 90퍼센트 지점 이후를 validation으로 둔다.
- metadata에 `feature_columns`, `horizons`, `seq_len`, 구조 파라미터, scaler 경로를 저장한다.

추론 입력:

- wrapper는 `get_standard_training_data(df.copy())`로 피처를 다시 만든다.
- metadata가 있으면 피처, horizon, seq_len, 구조 파라미터를 복원한다.
- 누락 피처가 있으면 오류를 낸다.

wrapper 출력:

- `{"tcn_1d": float, "tcn_3d": float, "tcn_5d": float, "tcn_7d": float}`
- `predict_batch()`도 ticker별 같은 딕셔너리를 반환한다.

확인 필요:

- `get_standard_training_data()`가 학습과 추론에서 완전히 같은 피처 계산 경로인지 고정 검증이 필요하다.
- TCN은 단기 모델로 쓰기 적합하지만 현재 `portfolio_logic.py`에서는 모든 horizon 확률을 평균내므로 1일/3일 강점이 희석될 수 있다.

### 3.3 PatchTST

구조:

- PyTorch 기반 PatchTST classifier.
- 기본 `seq_len=120`, horizon은 `[1, 3, 5, 7]`.
- 입력 피처 수는 17개로, 일봉 11개와 주봉/월봉 6개다.
- 출력은 horizon별 logits이며 wrapper에서 sigmoid를 적용한다.

학습 입력:

- 로컬 `train.py`는 DB 로드 후 `add_technical_indicators`, `add_multi_timeframe_features`를 직접 적용한다.
- train/validation은 ticker 기준으로 나눈다.
- scaler는 `MinMaxScaler`이고 train에만 fit한다.
- 로컬 `train.py`는 누락 피처를 경고만 하고 사용 가능한 피처로 시퀀스를 만들 수 있다.
- `train_kaggle.py`는 누락 피처를 오류로 처리한다.

추론 입력:

- wrapper는 `FEATURE_COLUMNS` 순서대로 DataFrame을 만든다.
- 누락 피처는 `0.0`으로 채운다.
- 모델 또는 scaler가 없거나 데이터가 부족하면 기본값 `0.5`를 반환한다.

wrapper 출력:

- `{"patchtst_1d": float, "patchtst_3d": float, "patchtst_5d": float, "patchtst_7d": float}`
- 반환 확률은 소수점 4자리로 반올림된다.

확인 필요:

- 로컬 학습은 누락 피처를 허용하고 추론은 0으로 채우는 반면, Kaggle 학습은 누락 피처를 오류로 처리한다. 평가용 파이프라인에서는 하나의 엄격한 규칙으로 통일해야 한다.
- `PatchTST` 디렉터리는 대문자인데 일부 import와 artifact 경로는 `patchtst` 소문자를 사용한다. Windows에서는 통과할 수 있지만 Linux/Kaggle에서는 실패할 수 있다.
- metadata 파일이 없어 feature set version, scaler version, 학습 기간을 별도로 남기기 어렵다.

### 3.4 iTransformer

구조:

- TensorFlow/Keras 기반 iTransformer classifier.
- 입력은 시계열 텐서, ticker id, sector id 3개다.
- 일반 Transformer와 달리 feature 축을 token처럼 보며, 거시/상관 구조에 초점을 둔다.
- 기본 horizon은 `[1, 3, 5, 7]`.

학습 입력:

- 로컬 `train.py`는 macro, market, breadth, correlation 계열 피처를 구성한다.
- 기본 후보 피처는 금리, VIX, DXY, credit spread, WTI, gold, breadth, correlation spike, recent loss, 수익률 계열 등이다.
- 로컬 `train.py`는 날짜 기준 time split을 사용하고 scaler를 train에만 fit한다.
- metadata에 `feature_names`, `horizons`, `seq_len`, ticker/sector 매핑, 구조 파라미터, signal horizon weight를 저장한다.
- `train_kaggle.py`도 metadata를 저장하나, ticker 기준 split을 쓴다.

추론 입력:

- wrapper는 metadata와 scaler의 `feature_names_in_`를 통해 피처 순서를 복원한다.
- `get_signals()`는 필요한 피처가 없으면 오류를 낸다.
- scaler 경로가 있으면 scaler 로드를 요구한다.

wrapper 출력:

- `{"itransformer_1d": float, "itransformer_3d": float, "itransformer_5d": float, "itransformer_7d": float}`
- output dimension과 metadata horizon 수가 다르면 wrapper가 horizon 목록을 보정한다.

확인 필요:

- 로컬 학습과 Kaggle 학습의 split 기준이 다르다. 평가용 walk-forward에서는 둘 다 같은 fold 정의를 따라야 한다.
- iTransformer의 강점은 단일 ticker 방향 예측보다 regime/risk-off 식별일 수 있으므로 leaderboard에 risk 관련 지표가 필요하다.

## 4. 학습 피처와 추론 피처 일치성

| 모델 | 학습 피처 출처 | 추론 피처 출처 | 현재 일치성 판단 | 평가용 조치 |
| --- | --- | --- | --- | --- |
| Transformer | `TRANSFORMER_TRAIN_FEATURES`, `DataLoader.create_dataset()` | scaler `feature_names_in_` 또는 config | 최신 scaler가 있으면 대체로 일치. metadata 부재와 silent drop 위험 있음 | feature set version과 실제 scaler 피처 목록을 예측 row에 기록 |
| TCN | `FEATURE_COLUMNS` 11개, metadata 저장 | metadata 또는 기본 11개, `get_standard_training_data()` | 비교적 명확함 | metadata 기반 검증을 필수화 |
| PatchTST | `FEATURE_COLUMNS` 17개 | wrapper 상수 17개, 누락 시 0 채움 | 규칙 불일치 있음 | 누락 피처는 평가에서 오류 처리. 0 채움은 운영 fallback으로만 제한 |
| iTransformer | metadata `feature_names` | metadata 또는 scaler feature names | 가장 명확함 | metadata 없는 artifact는 평가 대상에서 제외하거나 별도 표시 |

## 5. 공통 signal schema v0

저장 단위는 `모델 1개, ticker 1개, horizon 1개, asof_date 1개`당 row 1개다. 이 구조가 단독 모델 평가, 단순 평균 앙상블, 향후 MoE/Gating 모두에 가장 단순하게 연결된다.

필수 컬럼:

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `asof_date` | date | 피처 계산에 사용된 마지막 시장 날짜. 이 날짜 이후 데이터는 쓰지 않는다. |
| `decision_time` | timestamp | 실제 매매 판단 가능 시각. `asof_date`와 의미가 다르므로 호출자가 UTC 기준으로 명시 전달한다. |
| `run_id` | string | 동일 실행을 묶는 id. 예: `wf_2024q1_fold03_20260430`. |
| `model_ver` | string | artifact 또는 학습 설정 버전. 커밋 hash, artifact timestamp, 수동 버전을 허용한다. |
| `ticker` | string | 종목 코드. |
| `model_name` | string | `transformer`, `tcn`, `patchtst`, `itransformer`, `ensemble_mean` 등. |
| `horizon` | int | 예측 horizon 일수. 기본 후보는 `1`, `3`, `5`, `7`. |
| `prob_up` | float | 해당 horizon 뒤 종가가 asof 종가보다 상승할 확률. 0에서 1 사이. |
| `confidence` | float | 확신도. v0 기본값은 `abs(prob_up - 0.5) * 2`. |
| `raw_score` | float | 모델 원 출력에서 온 점수. 현재 wrapper는 대부분 확률이므로 v0에서는 `prob_up`과 같게 둘 수 있다. |
| `signal` | string | `buy`, `hold`, `sell` 중 하나. v0 기본은 threshold 미통과 시 `hold`. |
| `feature_set_ver` | string | 예: `technical_daily_v1`, `technical_mtf_v1`, `macro_corr_v1`. |
| `train_window` | string | 학습 기간. v0에서는 `YYYY-MM-DD..YYYY-MM-DD` 문자열로 둔다. |
| `eval_window` | string | 평가 fold 기간. v0에서는 `YYYY-MM-DD..YYYY-MM-DD` 문자열로 둔다. |

권장 추가 컬럼:

| 컬럼 | 타입 | 설명 |
| --- | --- | --- |
| `fold_id` | string | walk-forward fold 식별자. |
| `seq_len` | int | 실제 입력 길이. |
| `scaler_ver` | string | scaler artifact 식별자. 없으면 `unknown`. |
| `artifact_path` | string | 평가에 사용한 모델 artifact 경로 또는 alias. |
| `feature_count` | int | 실제 입력 피처 수. |
| `prediction_status` | string | `ok`, `fallback`, `error`. wrapper fallback 출력은 `fallback`으로 명시 기록한다. |
| `error_message` | string | 예측 실패 시 짧은 사유. |

v0에서는 `prob_up`이 `0.5`라는 이유만으로 fallback을 자동 판단하지 않는다. 실제 모델이 정상적으로 0.5를 낼 수 있으므로, 호출자가 `prediction_status="fallback"` 또는 `prediction_status_map`으로 명시해야 한다.

v0 signal 산출 규칙:

- `prob_up >= buy_threshold`이고 `confidence >= confidence_threshold`이면 `buy`.
- `prob_up <= sell_threshold`이고 `confidence >= confidence_threshold`이면 `sell`.
- 나머지는 `hold`.
- threshold는 평가 구간에서 튜닝하지 않고 train fold에서만 정한다.

## 6. 모델별 목적 지표

공통 leaderboard는 모든 모델을 같은 포트폴리오 컬럼으로 비교한다. 다만 4개 모델의 역할이 다르므로, `objectives.py`의 `MODEL_OBJECTIVE_PROFILES`는 모델별 주 평가 horizon과 primary metric을 따로 정의한다. 이 profile은 모델 출력 horizon `[1, 3, 5, 7]` 자체를 바꾸지 않고, 목적 성적표에서 어떤 horizon을 중심으로 볼지만 정한다.

### 6.1 Transformer

역할: 범용 baseline. 모든 horizon에서 안정적인 기준선을 제공하고, 확률 자체가 믿을 만한지 확인한다.

Primary metric:

- Horizon별 Brier score 또는 log loss.
- 이유: 범용 baseline은 수익률 하나보다 확률 예측의 품질과 calibration이 중요하다. 이후 앙상블 평균이나 gating의 입력으로 쓰려면 확률이 과신되지 않아야 한다.

Secondary metrics:

- Horizon별 AUC, balanced accuracy, precision at top-k.
- Horizon별 rank IC.
- 전체 기간과 fold별 수익률 안정성.

Diagnostic metrics:

- ECE(Expected Calibration Error), reliability bin plot 데이터.
- Fold별 metric 표준편차.
- `prob_up` 분포의 쏠림 여부.

### 6.2 TCN

역할: 단기/국소 패턴 포착. 1일과 3일 high confidence 구간의 실제 성능을 본다.

Primary metric:

- 1일/3일 high confidence precision.
- 이유: TCN은 짧은 receptive field의 국소 패턴에 강점이 있으므로, 전체 평균보다 강한 신호를 냈을 때 맞는지가 핵심이다.

Secondary metrics:

- 1일/3일 top-k forward return.
- 1일/3일 precision at k.
- 단기 기대수익 대비 turnover.

Diagnostic metrics:

- 신호 발생률.
- high confidence 구간 표본 수.
- 연속 손실 구간에서 confidence가 과도하게 높아지는지 여부.

#### TCN Sweep Plan v0

목표:

- TCN의 과적합과 성능 정체를 줄이기 위한 작은 후보군을 먼저 고정한다.
- 대규모 학습을 바로 실행하지 않고, smoke subset에서 설정이 정상적으로 학습, 저장, 추론, 평가까지 이어지는지만 먼저 확인한다.
- 5일/7일 출력은 계속 유지하되, sweep primary 평가는 1일/3일 horizon에 둔다.

현재 구조 확인:

- `architecture.py`의 `TCNClassifier`는 dilated causal Conv1d 기반 `TemporalBlock`을 여러 층 쌓고, `AdaptiveAvgPool1d(1)` 뒤 linear head로 horizon별 logits를 낸다.
- 입력 shape은 `[batch, seq_len, features]`이고 내부에서 `[batch, features, seq_len]`로 변환한다.
- 기본 horizon은 `[1, 3, 5, 7]`이며 출력 schema는 `{"tcn_1d": float, "tcn_3d": float, "tcn_5d": float, "tcn_7d": float}`이다.
- 현재 기본 채널은 로컬 `train.py`, `train_kaggle.py`, `wrapper.py` 모두 `[32, 64, 64]`이다.
- 로컬 `train.py` 기본값은 `seq_len=60`, `epochs=20`, `batch_size=64`, `learning_rate=1e-3`, `kernel_size=3`, `dropout=0.2`, `weight_decay=0.0`, `patience=0`이다.
- `patience=0`은 기존 기본 동작을 크게 바꾸지 않기 위해 early stopping을 끄는 값이다. sweep에서는 `--patience 3` 또는 `--patience 5`를 명시한다.
- `train_kaggle.py`는 `weight_decay=1e-4`, `patience=7` early stopping을 기본 적용한다.
- 로컬 `train.py`와 `train_kaggle.py` 모두 best checkpoint 기준 `train_loss_best`, `val_loss_best`, `train_val_loss_gap`를 metadata에 남긴다.
- TCN 로컬 학습/추론은 `ticker/date` 중복을 허용하지 않는다. 여러 ticker를 한 번에 공통 `FeatureProcessor`에 넣으면 주봉/월봉 join에서 row가 증폭될 수 있으므로, TCN은 ticker별 표준 전처리 후 합치는 경로를 사용한다.
- `ticker/date` 중복이 발견되면 기본은 `ValueError`다. 완전히 동일한 중복 제거는 별도 옵션으로만 검토하고, smoke sweep 재실행 전에는 raw/processed row 수와 unique key 수가 같은지 먼저 확인한다.
- TCN preprocessing guard 이전에 만든 smoke sweep 결과는 중복 폭증 가능성이 있으므로 leaderboard 비교에서 무효로 표시하고, raw row 수, processed row 수, duplicate row 수, horizon별 label positive rate를 기록한 뒤 재실행한다.
- `wrapper.predict()`는 단일 ticker DataFrame만 받는다. 여러 ticker 추론은 ticker별 안전 전처리를 반복하는 `predict_batch()`를 사용한다.

입력 feature 판단:

- TCN feature는 `log_return`, OHLC 비율, 거래량 변화, 5/20/60 이동평균 비율, RSI, MACD 비율, Bollinger 위치 11개다.
- 모두 일봉 기반의 짧은 가격/거래량/모멘텀/평균회귀 피처이므로 단기/국소 패턴 목적과 대체로 맞는다.
- 다만 macro, sector, cross-asset 정보는 없으므로 risk-off 판단이나 중장기 regime 평가를 TCN primary로 두지 않는다.

과적합 원인 가설:

- 채널이 커질수록 작은 단기 패턴 데이터에 비해 capacity가 커져 train/val gap이 벌어질 수 있다.
- `seq_len=60`은 단기 모델 치고 긴 편이라, 1일/3일 label에는 불필요한 과거 구간을 외울 가능성이 있다.
- dropout 0.2와 weight decay 1e-4는 약한 regularization일 수 있다.
- validation loss가 개선되어도 high confidence coverage가 낮거나 turnover 비용이 커지면 실제 평가 성능은 개선되지 않을 수 있다.
- 로컬 `train.py`와 `train_kaggle.py`의 regularization 옵션 차이 때문에 같은 sweep 설정을 두 경로에서 바로 공정 비교하기 어렵다.

모델 크기 분류:

| 분류 | 채널 예 | 이번 v0 처리 |
| --- | --- | --- |
| 과적합 위험 큰 모델 | 최대 채널이 128 이상인 TCN 또는 `[64, 128, 128]` 이상급 설정 | 이번 smoke sweep 후보에서 제외하고, 과거 실험/비교 기준으로만 기록 |
| 안정 기준선 | `[32, 64, 64]` | 현재 기본값. 반드시 포함 |
| 안정 후보 | `[32, 32, 64]` | 중간층 capacity를 줄이는 1차 후보 |
| 경량 후보 | `[16, 32, 64]` | 과적합과 turnover 감소 여부를 보는 후보 |

Sweep grid v0:

| 파라미터 | 후보 |
| --- | --- |
| `channels` | `[32, 32, 64]`, `[32, 64, 64]`, `[16, 32, 64]` |
| `dropout` | `0.2`, `0.3`, `0.4` |
| `weight_decay` | `1e-4`, `5e-4`, `1e-3` |
| `patience` | `3`, `5` |
| `seq_len` | `60` 유지, 짧은 단기형 `30` 또는 `40` |
| `learning_rate` | 기본 `1e-3`, 안정성 확인용 `5e-4` |
| `batch_size` | 기본 `64`, 작은 subset smoke용 `32` |

첫 smoke sweep 추천 조합:

| sweep_id | 목적 | channels | dropout | weight_decay | patience | seq_len | lr | batch_size |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `tcn_smoke_baseline` | 현재 기본 기준선 | `[32, 64, 64]` | `0.2` | `1e-4` | `5` | `60` | `1e-3` | `64` |
| `tcn_smoke_mid_regularized` | 기본보다 regularization 강화 | `[32, 64, 64]` | `0.3` | `5e-4` | `5` | `60` | `1e-3` | `64` |
| `tcn_smoke_small_mid` | 중간층 capacity 축소 | `[32, 32, 64]` | `0.3` | `5e-4` | `5` | `60` | `1e-3` | `64` |
| `tcn_smoke_light_short` | 경량+단기형 | `[16, 32, 64]` | `0.3` | `5e-4` | `3` | `40` | `1e-3` | `32` |
| `tcn_smoke_strong_reg` | 과적합 억제 상한 확인 | `[32, 32, 64]` | `0.4` | `1e-3` | `3` | `30` | `5e-4` | `32` |

성공 기준:

- `val_loss` 단독 개선을 성공으로 보지 않는다.
- `train_loss - val_loss` gap이 기준선보다 줄어야 한다.
- 1일/3일 `high_confidence_precision`이 개선되어야 한다.
- `high_confidence_coverage >= 0.05`를 유지해야 한다.
- 비용 반영 `net_return`이 개선되어야 한다.
- `turnover`가 비용을 압도하지 않는지 확인한다.
- MDD가 기준선보다 과도하게 악화되면 탈락시킨다.
- 5일/7일 성능은 참고 컬럼으로만 남기고 primary 결정에 쓰지 않는다.

Sweep 결과 컬럼:

| 컬럼 | 설명 |
| --- | --- |
| `sweep_id` | 설정 식별자 |
| `model_name` | 항상 `tcn` |
| `channels` | TCN 채널 목록 |
| `dropout` | dropout |
| `weight_decay` | Adam weight decay |
| `patience` | 개선 중단 patience. CLI와 metadata key 모두 `patience`로 통일한다. |
| `seq_len` | 입력 sequence 길이 |
| `learning_rate` | learning rate |
| `batch_size` | batch size |
| `train_window` | 학습 기간 |
| `validation_window` | validation 기간 |
| `train_loss_best` | best checkpoint 기준 train loss |
| `val_loss_best` | best validation loss |
| `train_val_loss_gap` | `val_loss_best - train_loss_best` |
| `horizon` | `1`, `3`, `5`, `7` |
| `high_confidence_precision` | high confidence 구간 precision |
| `high_confidence_coverage` | high confidence 구간 비율 |
| `net_return` | 비용 반영 누적 수익률 |
| `turnover` | 거래 회전율 |
| `cost_paid` | 거래비용 |
| `mdd` | 최대 낙폭. 부호는 `portfolio_metrics()` 기준 유지 |
| `objective_score` | `objectives.py`의 TCN profile 기준 점수 |
| `guardrail_pass` | TCN objective guardrail 통과 여부 |
| `artifact_path` | smoke artifact 경로 또는 alias |
| `prediction_run_id` | evaluation runner 입력 예측 run id |
| `note` | 실패 원인, 피처/데이터 주의 사항 |

evaluation 연결:

- 학습 산출물은 기존 wrapper와 호환되도록 `model.pt`, `scaler.pkl`, `metadata.json`을 유지한다.
- metadata에는 최소 `feature_columns`, `horizons`, `seq_len`, `kernel_size`, `dropout`, `channels`, `weight_decay`, `patience`, `learning_rate`, `batch_size`, `tickers`, `train_loss_best`, `val_loss_best`, `train_val_loss_gap`, `model_path`, `scaler_path`가 있어야 한다. Kaggle 경로처럼 ticker 필터가 없으면 `tickers=null`로 둔다.
- sweep별 예측은 wrapper 출력 dict를 `normalize_signal_output()`으로 Signal Schema v0에 맞춰 저장한다.
- `run_smoke_evaluation()`으로 1일/3일 모델 단독 Top-k, `universe_equal`, leaderboard, objective frame까지 연결한다.
- fallback이나 모델/scaler 누락은 `prediction_status="fallback"` 또는 `error`로 남기고 기본 평가는 `ok`만 사용한다.

필요한 코드 변경 판단:

- 구현 완료: 로컬 `train.py`에 `--weight-decay`, `--patience`를 추가했다.
- 구현 완료: 로컬 `train.py`에서 `--tickers`를 실제 데이터 필터에 적용한다.
- 구현 완료: 로컬 `train.py`와 `train_kaggle.py`가 sweep 비교용 loss와 설정 metadata를 저장한다.
- 큰 sweep 실행은 아직 하지 않는다.
- Kaggle 경로는 기본 patience가 `7`이므로 smoke sweep에서는 config에서 `3` 또는 `5`를 명시해야 한다.

#### TCN Short-Horizon Feature Plan v0

목표:

- TCN을 1일/3일 단기 신호 모델로 평가하기 위해 현재 `technical_daily_v1` 11개 피처를 재검토한다.
- 새 학습 전에는 학습/추론 양쪽에서 같은 피처를 엄격하게 만들 수 있는지 먼저 검증한다.
- 없는 피처를 0으로 채우는 fallback은 평가에서는 금지한다. 누락 피처는 오류로 처리한다.

현재 `technical_daily_v1`:

| 피처 | 단기 목적 판단 | 처리 |
| --- | --- | --- |
| `log_return` | 최근 가격 반응. 1일/3일 목적에 적합 | 유지 |
| `open_ratio`, `high_ratio`, `low_ratio` | 전일 종가 대비 당일 캔들 위치. 단기 변동 포착 가능 | 유지하되 candle/body/wick 피처로 보강 |
| `vol_change` | 거래량 급변 포착. 단기 목적에 적합 | 유지 |
| `ma5_ratio` | 단기 이격도. 1일/3일 목적에 적합 | 유지 |
| `ma20_ratio` | 중기 이격도. 단기에는 보조 | 유지 후보 |
| `ma60_ratio` | 느린 추세 피처. 1일/3일 primary에는 둔함 | 제거 후보 또는 plus 버전에만 유지 |
| `rsi`/`rsi_14` | 과열/침체. 14일은 다소 느림 | `rsi_7` 추가 검토 |
| `macd_ratio` | 느린 모멘텀. 단기에는 보조 | 유지 후보 |
| `bb_position` | 20일 밴드 위치. 단기 반전/돌파에 보조 | 유지 후보 |

현재 파이프라인에서 이미 계산되는 후보:

- 즉시 사용 가능: `ret_1d`, `log_return`, `vol_change`, `intraday_vol`, `rsi_14`, `ma5_ratio`, `ma20_ratio`, `ma60_ratio`, `bb_position`, `atr_14`.
- 새 계산 필요: `ret_2d`, `ret_3d`, `ret_5d`, `volume_z_5`, `volume_z_10`, `intraday_volatility`, `candle_body`, `upper_wick`, `lower_wick`, `ma10_ratio`, `rolling_vol_5`, `rsi_7`.
- 이벤트 원천: `event_calendar` 테이블에는 `EARNINGS`, `FOMC`, `CPI`, `GDP`, `PCE`가 있으나 현재 DB의 `EARNINGS`는 2025년 9월 이후 구간에만 있고, smoke 학습 기간인 2021~2024에는 직접 쓸 수 없다.
- `macroeconomic_indicators`에는 `cpi`, `core_cpi` 컬럼이 있으나 월간 값이며 event window flag가 아니라 level 값이다.

후보 feature set:

| feature_set_ver | 목적 | 컬럼 후보 | 판단 |
| --- | --- | --- | --- |
| `technical_daily_v1` | 현재 기준선 | 현재 11개 | baseline 유지 |
| `tcn_short_horizon_v1` | 1일/3일 단기 가격/거래량/캔들 반응 강화 | `ret_1d`, `ret_2d`, `ret_3d`, `ret_5d`, `log_return`, `vol_change`, `volume_z_5`, `volume_z_10`, `intraday_volatility`, `candle_body`, `upper_wick`, `lower_wick`, `ma5_ratio`, `ma10_ratio`, `rolling_vol_5`, `rsi_7`, `rsi_14` | 구현 완료. 다음 smoke 1순위 |
| `tcn_short_horizon_trimmed_v1` | 느린 피처 제거 효과 확인 | `tcn_short_horizon_v1`에서 `rsi_14`만 보조로 두고 `ma20_ratio`, `ma60_ratio`, `macd_ratio`, `bb_position` 제외 | 단기 coverage 회복 확인용 |
| `tcn_short_horizon_plus_event_v1` | 이벤트 근접 구간 단기 반응 확인 | `tcn_short_horizon_v1` + `days_to_earnings`, `event_window_flag_fomc`, `event_window_flag_cpi` | 이벤트 데이터 기간/안정성 문제로 v1에서는 보류 |

코드 영향 범위:

- `AI/modules/signal/models/TCN/preprocessing.py`: TCN 전용 feature set 생성 함수를 추가하는 위치가 가장 안전하다. ticker별 처리와 `ticker/date` 중복 방어를 유지한다.
- `AI/modules/signal/models/TCN/train.py`: `--feature-set-ver` 인자와 feature column resolver가 필요하다. metadata에 `feature_set_ver`, `feature_columns`, `feature_count`를 저장한다.
- `AI/modules/signal/models/TCN/train_kaggle.py`: 로컬과 같은 feature set 이름과 컬럼 순서를 지원해야 한다.
- `AI/modules/signal/models/TCN/wrapper.py`: metadata의 `feature_set_ver`와 `feature_columns`를 읽고, 추론 시 동일 feature set을 생성해야 한다. 누락 피처는 오류로 처리한다.
- `AI/modules/signal/evaluation/schema.py`: 이미 `feature_set_ver`, `feature_count`를 담을 수 있으므로 추가 변경은 불필요하다.
- `AI/modules/signal/evaluation/feature_sets.py`: 아직 파일이 없으므로 후속 구현에서 feature set 이름, 컬럼 순서, 생성 함수를 명시하는 새 파일 후보로 둔다.

구현 v0 계약:

- `technical_daily_v1`과 `tcn_short_horizon_v1`은 `AI/modules/signal/models/TCN/preprocessing.py`에서 명시적으로 정의한다.
- 로컬 `train.py`와 `train_kaggle.py`는 `--feature-set-ver`를 받으며, metadata에 `feature_set_ver`, `feature_columns`, `feature_count`를 저장한다.
- wrapper는 metadata의 `feature_set_ver`, `feature_columns`, `feature_count`를 읽고 등록된 feature set 계약과 정확히 일치하는지 검증한다. metadata가 없거나 구버전이면 `technical_daily_v1`로 해석한다.
- scaler의 `n_features_in_` 또는 `feature_names_in_`가 있으면 metadata의 feature 수와 순서가 다른 artifact 조합은 로드 시점에 실패한다.
- 없는 피처 컬럼은 0으로 채우지 않고 `ValueError`로 실패한다. rolling warm-up 등 계산 과정에서 생긴 NaN/inf만 입력 피처 컬럼 범위에서 0으로 정리한다.
- row count guard와 `ticker/date` 중복 guard는 피처셋 선택과 무관하게 유지한다.

기존 artifact 호환성:

- `feature_columns` 수와 순서가 바뀌면 기존 `model.pt`와 `scaler.pkl`은 호환되지 않는다.
- wrapper는 metadata의 `feature_columns`를 읽으므로 새 artifact는 로드 가능하지만, metadata가 없는 과거 artifact는 기본 11개 `technical_daily_v1`로만 해석해야 한다.
- feature set이 바뀐 예측 row는 반드시 `feature_set_ver`를 `tcn_short_horizon_v1`처럼 별도 기록한다.

첫 feature smoke 추천:

| smoke_id | feature_set_ver | 모델 설정 | 목적 |
| --- | --- | --- | --- |
| `tcn_feature_baseline_daily_v1` | `technical_daily_v1` | `tcn_smoke_small_mid_v1` 설정 | 기존 기준선 재현 |
| `tcn_feature_short_v1_small_mid` | `tcn_short_horizon_v1` | `[32, 32, 64]`, dropout `0.3`, weight_decay `5e-4`, seq_len `60` | val loss가 가장 낮았던 구조에 단기 피처 적용 |
| `tcn_feature_short_v1_light_short` | `tcn_short_horizon_v1` | `[16, 32, 64]`, dropout `0.3`, weight_decay `5e-4`, seq_len `40` | 3일 buy 후보가 가장 많이 살아난 구조에 단기 피처 적용 |

성공 기준:

- 1일/3일 high confidence coverage가 최소 `0.05` 이상으로 회복된다.
- high confidence precision, 비용 반영 net return, turnover, MDD를 함께 본다.
- 5일/7일은 참고 지표이며, 1일/3일 개선과 충돌하면 1일/3일을 우선한다.

### 6.3 PatchTST

역할: 중장기 추세와 cross-horizon 안정성. 5일/7일 순위 성능과 top-k 성과를 본다.

Primary metric:

- 5일/7일 rank IC 또는 Spearman correlation.
- 이유: PatchTST는 긴 sequence와 patch 구조를 쓰므로, 개별 확률 calibration보다 종목 간 상대 순위가 더 중요한 평가축이다.

Secondary metrics:

- 5일/7일 top-k forward return.
- NDCG@k.
- top quantile과 bottom quantile의 spread return.

Diagnostic metrics:

- horizon별 예측 단조성. 예: 7일 확률이 항상 1일보다 낮거나 높은 식의 구조적 편향.
- 누락 피처 0 채움 발생률.
- 반올림 전후 순위 변화.

### 6.4 iTransformer

역할: 거시/상관 구조와 risk-off 회피. 상승 종목 맞추기뿐 아니라 위험한 구간에서 노출을 줄이는 기여를 본다.

Primary metric:

- risk-off 구간 MDD 감소 기여도.
- 이유: iTransformer는 macro/correlation 피처를 쓰므로, 개별 종목 alpha보다 시장 위험 회피 능력을 별도 평가해야 공정하다.

Secondary metrics:

- regime별 AUC와 Brier score.
- 고 VIX 또는 breadth 악화 구간에서 현금 비중 증가가 손실을 줄였는지.
- 포트폴리오 volatility 감소율.

Diagnostic metrics:

- regime별 신호 분포.
- risk-on 구간에서 과도하게 보수적이어서 기회비용이 커지는지.
- 특정 macro 피처 결측/ffill 구간의 예측 안정성.

### 6.5 Model Objective & Feature Role v1

v1 공통 원칙:

- 모든 모델은 `1d`, `3d`, `5d`, `7d` 출력을 유지한다.
- 모델별 역할은 horizon 고정이 아니라 feature와 구조적 강점으로 구분한다.
- 공통 leaderboard는 모든 horizon을 남기고, objective frame은 모델별 primary objective와 guardrail을 별도로 붙인다.
- 특정 모델이 예상과 다른 horizon에서 강하면 그 horizon을 버리지 않고 MoE/Gating 후보로 남긴다.
- 2025년 이후 OOS 구간은 feature/threshold/model tuning에 쓰지 않는다.
- 없는 피처를 0으로 채우는 fallback은 평가에서 금지한다.
- `feature_set_ver`, `model_ver`, `train_window`, `eval_window`는 metadata와 signal schema에 모두 남긴다.

현재 feature 목록과 v1 역할:

| 모델 | 현재 feature 목록 | 유지 | 제거/축소 후보 | 추가 후보 | feature_set_ver 제안 |
| --- | --- | --- | --- | --- | --- |
| Transformer | `log_return`, OHLC 비율, `vol_change`, `ma5/20/60_ratio`, `rsi`, `macd_ratio`, `bb_position`, 주봉 4개, 월봉 2개 | 기술적 baseline 전체, 주월봉 context | 과도한 macro 확장은 baseline 역할을 흐릴 수 있어 보류 | `vix_change_rate`, `dxy_chg`, `us10y_chg` 같은 market state 최소 후보 | `transformer_technical_market_v1` |
| TCN | `technical_daily_v1` 11개 또는 `tcn_short_horizon_v1` 17개 | short return, candle, volume shock, intraday/short volatility | `ma60_ratio`, 느린 MACD/Bollinger는 plus 후보로 분리 | `ret_2d/3d/5d`, `volume_z_5/10`, `candle_body`, `upper_wick`, `lower_wick`, `rolling_vol_5`, `rsi_7` | `tcn_short_horizon_v1` |
| PatchTST | Transformer와 같은 17개 technical + 주월봉 피처 | 주월봉, trend stack, rolling context | 결측 피처 0 채움은 평가 금지. 단기 캔들만으로 축소하지 않음 | `ret_5d/10d/20d`, `rolling_vol_10/20`, sector relative strength, trend slope stack | `patchtst_swing_ranking_v1` |
| iTransformer | `us10y`, `us10y_chg`, `yield_spread`, `vix_close`, `vix_change_rate`, `dxy_close`, `dxy_chg`, `credit_spread_hy`, `wti_price`, `gold_price`, `nh_nl_index`, `ma200_pct`, `correlation_spike`, `recent_loss_ema`, `ret_1d`, `intraday_vol`, `log_return`, `surprise_cpi` | macro, breadth, cross-asset, correlation/risk state | 순수 candle/short return은 보조 context로만 유지 | sector breadth, sector_return_* 집계, VIX regime flag, yield/credit 변화율 안정화 | `itransformer_regime_corr_v1` |

평가 기준 v1:

| 모델 | 역할 | primary objective | primary metrics | secondary metrics | guardrail v1 |
| --- | --- | --- | --- | --- | --- |
| Transformer | 범용 baseline / calibration anchor | calibration | `ece`, `brier_score`, `log_loss` | horizon별 accuracy, net_return, Sharpe | 모든 horizon 기록, `missing_return_rate=0`, fallback row 제외, calibration metric 전부 존재 |
| TCN | local price action expert | high-confidence signal quality | `high_confidence_precision`, `high_confidence_coverage`, high-confidence net_return | top-k net_return, turnover, cost_paid, MDD | 모든 horizon 기록, coverage `>=0.05`를 적어도 1개 horizon에서 만족, 비용 반영 net_return과 turnover 동시 기록 |
| PatchTST | short-swing/weekly pattern ranking expert | cross-sectional ranking | `rank_ic_mean`, `top_bottom_spread` | top-k net_return, bottom-k return, horizon별 spread 안정성 | 모든 horizon 기록, rank metric 누락 없음, top-bottom spread 방향 확인, missing return 없음 |
| iTransformer | market regime / correlation / risk-state expert | downside protection | `mdd`, `calmar`, downside return | net_return, volatility, risk-off exposure behavior | 모든 horizon 기록, regime 데이터 없으면 portfolio MDD/Calmar로 대체, universe_equal 대비 MDD 악화 여부 기록 |

해석 규칙:

- `objective_score`는 같은 모델, 같은 feature_set_ver, 같은 objective 내부 sweep 비교에만 쓴다.
- 모델 간 직접 비교는 공통 leaderboard의 `net_return`, `sharpe`, `mdd`, `turnover`, `cost_paid`로 본다.
- TCN의 주 가설은 1일/3일 강한 신호지만 objective frame은 5일/7일도 버리지 않는다.
- PatchTST의 주 가설은 5일/7일 ranking이지만 1일/3일 rank IC와 top-k 성과도 기록한다.
- iTransformer는 risk regime label이 부족한 v0에서는 portfolio MDD/Calmar를 primary proxy로 둔다.

`objectives.py` v1 반영:

- `MODEL_OBJECTIVE_PROFILES`의 모든 모델 `horizons`는 `[1, 3, 5, 7]`로 둔다.
- 모델별 주력 가설은 `primary_hypothesis_horizons`로 분리한다. Transformer는 `[1, 3, 5, 7]`, TCN은 `[1, 3]`, PatchTST는 `[5, 7]`, iTransformer는 `[3, 5, 7]`이다.
- `record_all_horizons=True`로 모든 horizon 결과를 objective frame에 기록한다.
- `build_model_objective_frame()`은 `evaluated_horizons`와 `row_count`는 전체 horizon 기준으로 남기고, primary metric과 guardrail은 `primary_hypothesis_horizons` 기준으로 집계한다.
- `best_horizon`은 전체 기록 horizon 중 primary metric이 가장 좋은 horizon을 참고값으로 남긴다.
- `horizon_metric_summary`는 아직 복잡도를 늘리지 않고 후속 TODO로 둔다.

구현 우선순위:

1. 완료: `objectives.py` profile v1 반영. 모든 horizon 기록, primary 가설 horizon 별도 필드 추가.
2. Transformer/PatchTST/iTransformer artifact metadata 보강: `feature_set_ver`, `feature_columns`, `feature_count`, `train_window`.
3. PatchTST 평가 fallback 제거: 누락 피처 0 채움은 평가 경로에서 `prediction_status="fallback"` 또는 오류로 분리.
4. 모델별 feature set resolver 추가: TCN처럼 학습/추론이 같은 컬럼 순서를 공유하게 만든다.
5. runner/leaderboard에 `all_horizon_metrics` 요약 컬럼을 추가한다.

smoke 실험 순서:

1. TCN `tcn_short_horizon_v1` feature smoke. 이미 구현된 피처셋이므로 가장 먼저 검증한다.
2. Transformer `transformer_technical_market_v1` metadata-only smoke. baseline calibration anchor가 흔들리지 않는지 본다.
3. PatchTST `patchtst_swing_ranking_v1` feature smoke. rank IC와 top-bottom spread가 모든 horizon에서 계산되는지 본다.
4. iTransformer `itransformer_regime_corr_v1` availability smoke. macro/breadth/cross-asset 결측과 regime proxy 사용 가능성을 먼저 검증한다.
5. 네 모델 OOF signal을 같은 leaderboard와 objective frame으로 묶고, 예상 외 강한 horizon을 MoE 후보로 표시한다.

## 7. Walk-forward 백테스트 설계

### 7.1 Split 방식

v0 권장 방식:

- 동일 ticker universe와 동일 날짜 캘린더를 4개 모델에 공통 적용한다.
- fold는 시간 기준으로 만든다.
- 기본은 expanding train window와 고정 eval window다.
- 예: train 2018-01-01부터 2022-12-31, eval 2023-01-01부터 2023-03-31. 다음 fold는 train 종료를 3개월 늘리고 eval도 다음 3개월로 이동한다.

rolling window가 필요한 경우:

- 최근 regime 적응을 평가할 때 train window를 최근 3년 또는 5년으로 고정한다.
- 단, 모델 간 비교에서는 expanding과 rolling을 섞지 않는다.

Embargo 규칙:

- `max_horizon=7`이면 train label이 eval 시작 이후 가격을 보지 않도록 eval 시작 전 최소 7 거래일의 label 생성 샘플을 제거한다.
- 즉 train sample은 `sample_asof_date + horizon <= train_end_date`를 만족해야 한다.

### 7.2 Out-of-fold 예측 저장 방식

각 fold에서 다음 순서로 저장한다.

1. train 기간으로 scaler와 모델을 학습하거나 이미 해당 fold artifact가 있으면 로드한다.
2. eval 기간의 각 `asof_date`에 대해 ticker별로 과거 `seq_len`만 사용해 예측한다.
3. horizon별 row를 `signal schema v0`로 저장한다.
4. 백테스트와 leaderboard는 저장된 예측 parquet/csv만 읽는다.

권장 저장 경로:

- 예측값: `AI/backtests/results/signal_evaluation/predictions/{run_id}.parquet`
- leaderboard: `AI/backtests/results/signal_evaluation/leaderboard.csv`
- 상세 metric: `AI/backtests/results/signal_evaluation/metrics/{run_id}.json`

위 경로는 결과물 성격이므로 커밋 대상에서 제외해도 된다.

### 7.3 Lookahead bias 방지 규칙

- `asof_date` 이후 가격, 거래량, macro, breadth, sector 정보는 feature에 넣지 않는다.
- rolling/ewm/ffill 계산은 ticker별 또는 날짜 기준으로 과거 방향만 허용한다.
- scaler는 train fold에만 fit한다.
- feature selection도 train fold에서만 결정한다.
- threshold와 top-k cutoff도 train fold 또는 이전 fold 결과로만 정한다.
- eval fold의 forward return은 metric 계산에만 쓰고 signal 생성에는 쓰지 않는다.
- 같은 `asof_date` 종가로 feature를 만들었다면 매매 체결은 다음 거래일 기준으로 둔다.

### 7.4 Horizon별 보유 기간

모델 평가용 v0에서는 horizon별 백테스트를 분리한다.

- 1일 horizon: 다음 거래일 체결 후 1거래일 보유.
- 3일 horizon: 다음 거래일 체결 후 3거래일 보유.
- 5일 horizon: 다음 거래일 체결 후 5거래일 보유.
- 7일 horizon: 다음 거래일 체결 후 7거래일 보유.

중복 포지션 처리:

- v0 단순화: 같은 ticker에 이미 해당 horizon 포지션이 있으면 새 진입을 막거나 기존 포지션을 target weight로 리밸런싱한다.
- leaderboard 비교 목적에서는 첫 버전은 `매 리밸런싱 시점 target weight 재계산` 방식이 더 구현하기 쉽다.

### 7.5 Top-k 매수 방식

기본 방식:

- 매 리밸런싱일에 ticker별 `prob_up`을 내림차순 정렬한다.
- `prob_up >= buy_threshold`와 `confidence >= confidence_threshold`를 모두 만족하는 종목 중 top-k를 산다.
- 선택 종목은 동일 비중 또는 시그널 강도 비례 비중으로 둔다.
- 후보가 없으면 현금 보유한다.
- v0 구현은 `backtest_top_k_signals()`에 들어 있으며, `equal`, `confidence`, `prob_excess` weighting을 지원한다.
- `sell` signal은 v0에서 short이 아니라 미보유 또는 기존 보유 해제로 해석한다.
- v0는 `forward_return`만 사용하는 horizon별 decision-period 백테스트다. 일별 mark-to-market curve는 만들지 않고, 일별 가격 경로가 들어오면 별도 v1로 확장한다.
- `forward_return`이 없는 signal row는 기본적으로 오류로 처리한다. 실험 편의상 제외하려면 `missing_return_policy="drop"`을 명시하고 누락률을 함께 기록한다.

권장 기본값:

- `top_k=3` 또는 `5`.
- `buy_threshold`는 고정값 0.6부터 시작하되, 모델별 threshold 비교는 train fold에서만 정한다.
- 모델 간 공정 비교에서는 동일 threshold 버전과 모델별 train-optimized threshold 버전을 분리해 leaderboard에 표시한다.

### 7.6 Confidence threshold 방식

v0 confidence:

- `confidence = abs(prob_up - 0.5) * 2`

threshold 방식:

- 절대 기준: `confidence >= 0.2` 같은 고정값.
- 분위수 기준: train fold 예측 confidence 상위 20퍼센트.

공정 비교에서는 분위수 기준을 우선 추천한다. 모델마다 확률 분포의 폭이 다르므로 같은 절대 confidence가 같은 의미가 아닐 수 있다.

### 7.7 거래비용과 슬리피지

v0 가정:

- 수수료: 15bp per side.
- 슬리피지: 5bp per side.
- 총 비용: 매수 20bp, 매도 20bp.

Leaderboard에는 비용 전후 수익률을 모두 남긴다.

### 7.8 리밸런싱 주기

평가 목적 기본값:

- 1일/3일 horizon: 매일 리밸런싱.
- 5일/7일 horizon: 주 1회 또는 horizon 주기 리밸런싱.

공정 비교 기본 leaderboard는 horizon별 자연 보유 기간을 따른다. 운영형 leaderboard에는 `daily rebalance`와 `weekly rebalance`를 별도 실험으로 분리한다.

### 7.9 모델별 단독 백테스트

각 모델은 같은 OOF 예측 테이블에서 `model_name`만 필터링해 평가한다.

- Transformer: 모든 horizon의 baseline 성과와 calibration.
- TCN: 모든 horizon의 high confidence 성과를 기록하되, local price action feature가 어느 horizon에서 살아나는지 확인.
- PatchTST: 모든 horizon의 top-k와 rank 성과를 기록하되, 5일/7일 swing-ranking 가설을 별도로 표시.
- iTransformer: 모든 horizon의 risk-adjusted 성과와 risk-off regime에서 exposure 조절 효과.

### 7.10 단순 평균 앙상블 백테스트

v0 ensemble:

- 같은 `asof_date`, `ticker`, `horizon`의 `prob_up`을 단순 평균한다.
- 누락 모델이 있으면 v0에서는 해당 row를 제외하고 평균하지 않는다. 즉 모든 모델 예측이 있는 row만 사용한다.
- 결과는 `model_name="ensemble_mean"`으로 signal schema에 저장한다.

이 방식은 MoE 전 단계 baseline으로 반드시 필요하다.

### 7.11 MoE/Gating 확장 지점

MoE는 예측 생성 단계가 아니라 저장된 OOF 예측을 읽는 scoring 단계에서 붙인다.

- 입력: 모델별 `prob_up`, `confidence`, 최근 OOF loss, regime feature.
- 출력: 모델별 weight.
- 결합: `ensemble_prob = sum(model_prob * model_weight)`.

중요한 점은 gating 학습에도 OOF 예측만 사용해야 한다는 것이다. 같은 기간을 학습한 base model의 in-sample 예측으로 gating을 학습하면 누수가 발생한다.

## 8. Leaderboard 산출 구조

Leaderboard row 단위는 `실험 1개, 모델 1개, horizon 1개, eval window 1개`다.

권장 컬럼:

| 컬럼 | 설명 |
| --- | --- |
| `leaderboard_run_id` | leaderboard 산출 실행 id |
| `prediction_run_id` | 사용한 OOF 예측 run id |
| `model_name` | 모델명 또는 앙상블명 |
| `benchmark_name` | `universe_equal` 같은 비모델 기준선 이름 |
| `strategy_name` | 모델명, weighting, top-k를 묶은 비교용 전략 이름 |
| `model_ver` | artifact 버전 |
| `feature_set_ver` | 피처셋 버전 |
| `train_window` | 학습 기간 |
| `validation_window` | validation 또는 eval 기간 |
| `horizon` | 평가 horizon |
| `weighting` | `equal`, `confidence`, `prob_excess` 등 비중 방식 |
| `top_k` | 선택 종목 수 |
| `periods_per_year` | horizon별 decision-period 연환산 기준 |
| `primary_metric_name` | 모델 목적에 맞는 primary metric 이름 |
| `primary_metric_value` | primary metric 값 |
| `secondary_metrics` | JSON 문자열. AUC, precision@k, rank IC 등 |
| `cumulative_return` | 누적 수익률 |
| `mdd` | 최대 낙폭 |
| `sharpe` | Sharpe |
| `turnover` | 회전율 |
| `gross_return` | 거래비용 전 수익률 |
| `net_return` | 거래비용 후 수익률. v0에서는 `cumulative_return`과 같은 의미로 둔다. |
| `cost_paid` | 총 거래비용 |
| `missing_return_rate` | signal row 중 realized forward return이 없어 평가에서 빠진 비율 |
| `selected_periods` | 최소 1종목 이상 선택한 period 수 |
| `total_periods` | 평가 period 수 |
| `cash_period_rate` | 선택 종목이 없어 현금으로 둔 period 비율 |
| `regime_metrics` | JSON 문자열. risk-on/off, high VIX 등 |
| `coverage` | 예측 성공 row 비율 |
| `fallback_rate` | 기본값 0.5 또는 fallback 사용 비율 |
| `note` | 확인 사항이나 실험 메모 |

v0 구현:

- `build_leaderboard_row()`는 `backtest_top_k_signals()` 또는 `universe_equal_benchmark()` 결과 1개를 leaderboard row 1개로 요약한다.
- `build_leaderboard()`는 여러 결과를 묶고, 기본적으로 비용 반영 후 성과인 `net_return` 기준 내림차순으로 정렬한다. `primary_metric_name="sharpe"`처럼 대체 정렬 기준도 명시할 수 있다.
- `save_leaderboard()`는 csv 저장만 지원하며, 저장 전 `LEADERBOARD_V0_COLUMNS`와 정확히 일치하는지 검증한다.
- Transformer 단독은 모델 baseline이고, `universe_equal`은 universe exposure baseline이다.
- `ensemble_mean`과 MoE는 후속 단계에서 같은 leaderboard 형식으로 비교한다.

`build_model_objective_frame()`은 leaderboard 위에 모델별 목적 성적표를 한 층 더 만든다. leaderboard row를 모델과 전략 단위로 묶고, profile horizon만 필터링해 `primary_metric_value`, `objective_score`, `guardrail_pass`, `guardrail_reasons`를 산출한다. calibration, high-confidence, ranking 지표는 선택 입력인 `metric_frame`에서 붙이며, portfolio 지표는 leaderboard에서 가져온다.

요약 leaderboard는 다음 집계를 추가로 만든다.

- 모델별 전체 평균 순위.
- horizon별 최고 모델.
- regime별 최고 모델.
- 단순 평균 앙상블이 단독 모델보다 개선한 항목.
- 비용 반영 후에도 유효한 항목.

## 9. 기존 코드와 연결 가능한 최소 구현 방향

대규모 구현 대신 `AI/modules/signal/evaluation/` 하위에 얇은 모듈을 추가하는 방향을 추천한다.

### 9.1 제안 모듈

`AI/modules/signal/evaluation/schema.py`

- 구현 완료: `SIGNAL_SCHEMA_V0_REQUIRED_COLUMNS`, `SIGNAL_SCHEMA_V0_OPTIONAL_COLUMNS`, `SIGNAL_SCHEMA_V0_COLUMNS`.
- 구현 완료: `parse_prediction_key()`, `calculate_confidence()`, `calculate_signal()`.
- 구현 완료: wrapper 출력 딕셔너리를 row 단위 schema로 펼치는 `normalize_signal_output()`.
- 구현 완료: `validate_signal_frame()`로 필수 컬럼, 확률/확신도 범위, horizon, signal, prediction status를 검증.
- 보강 완료: `decision_time`은 자동 추정하지 않고 호출자가 명시 전달한다.
- 보강 완료: `prediction_status`, `prediction_status_map`, `error_message`, `error_message_map`으로 fallback/error 상태를 row에 기록한다.

`AI/modules/signal/evaluation/diagnostics.py`

- 구현 완료: `build_signal_diagnostics_frame()`으로 모델/horizon별 signal, label, 예측 분포 진단표를 만든다.
- group key는 `model_name`, `horizon`이다.
- 진단 컬럼은 `row_count`, prediction status별 count, `fallback_rate`, `error_rate`, `prob_up` 평균/표준편차/최소/최대/분위수, `near_half_rate`, `high_confidence_coverage`, `buy_candidate_count`, `sell_candidate_count`, `label_positive_rate`, `missing_return_rate`, `diagnostic_status`, `diagnostic_reasons`를 포함한다.
- `error_rate > 0`, `missing_return_rate > 0`, label이 전부 양수/음수인 경우는 `fail`이다.
- fallback row, label positive rate 비정상 범위, high confidence coverage 0, buy 후보 없음, 낮은 `prob_up` 표준편차, 0.5 근처 과밀은 `warn`이다.
- 이 진단표는 무효 smoke를 사람이 수동으로 발견하지 않도록 runner가 leaderboard와 함께 저장한다.

`AI/modules/signal/evaluation/metrics.py`

- 구현 완료: `classification_metrics(y_true, prob_up)`로 count, positive rate, Brier, log loss, accuracy, precision, recall, f1 계산.
- 구현 완료: `high_confidence_metrics(y_true, prob_up)`로 강한 확신 구간 coverage, precision, accuracy, avg confidence 계산.
- 구현 완료: `ranking_metrics(signal_frame, returns_frame, k)`로 top-k 수익률, bottom-k 수익률, spread, rank IC 계산.
- 구현 완료: `calibration_metrics(y_true, prob_up)`로 ECE, MCE, Brier, bin별 calibration 통계 계산.
- 구현 완료: `portfolio_metrics(equity_curve, trades)`로 누적수익률, 연환산 지표, 변동성, Sharpe, MDD, Calmar, turnover, cost 계산.
- 구현 완료: `portfolio_metrics()`는 horizon별 `periods_per_year=252 / horizon`처럼 실수 period 수를 받을 수 있다.

`AI/modules/signal/evaluation/walk_forward.py`

- `WalkForwardSplit` 데이터 클래스.
- fold 생성 함수.
- 모델 wrapper를 받아 OOF prediction frame을 만드는 함수.
- lookahead embargo 검사 함수.

`AI/modules/signal/evaluation/backtest.py`

- 구현 완료: `backtest_top_k_signals()`로 signal frame과 realized forward return을 조인해 고정 Top-k 백테스트를 계산한다.
- 구현 완료: `equal`은 종목 선택 능력 기준선이고, `confidence`와 `prob_excess`는 시그널 강도에 더 큰 비중을 주는 실험이다.
- 구현 완료: round-trip 거래비용은 `2 * cost_bps_per_side / 10000`으로 차감한다.
- 구현 완료: `missing_return_policy` 기본값은 `"error"`이며, `"drop"`을 명시하면 누락 row를 제외하고 `missing_return_rate`를 기록한다.
- 구현 완료: `prediction_status`는 기본적으로 `ok`만 사용하고, fallback 포함 실험은 호출자가 명시한다.
- 구현 완료: `universe_equal_benchmark()`는 같은 horizon의 전체 universe 평균 forward return을 시장/universe 노출 기준선으로 계산한다.
- 모델별 단독 백테스트는 `model_name="transformer"` 같은 필터로 처리하고, `ensemble_mean`은 후속 단계에서 평균 signal frame을 만든 뒤 같은 함수로 평가한다.

`AI/modules/signal/evaluation/leaderboard.py`

- 구현 완료: `build_leaderboard_row()`로 backtest result 1개를 비교 가능한 row 1개로 요약한다.
- 구현 완료: `build_leaderboard()`로 여러 모델/비중 방식/기준선을 같은 표로 묶고 `primary_metric_value` 내림차순으로 정렬한다. 기본 primary metric은 `net_return`이고, 호출자가 `sharpe` 등으로 바꿀 수 있다.
- 구현 완료: `save_leaderboard()`로 csv 저장을 지원하며, 저장 전 leaderboard v0 컬럼 누락과 추가 컬럼을 오류로 처리한다.
- `missing_return_rate`, `gross_return`, `net_return`, `cost_paid`, `turnover`, `periods_per_year`는 leaderboard 해석 필수 컬럼이다.

`AI/modules/signal/evaluation/objectives.py`

- 구현 완료: `MODEL_OBJECTIVE_PROFILES`로 Transformer, TCN, PatchTST, iTransformer의 목적, 전체 기록 horizon, 주력 가설 horizon, primary metric, guardrail을 정의한다.
- 구현 완료: 모든 모델의 기록 horizon은 `[1, 3, 5, 7]`로 통일하고, 현재 subset horizon은 `primary_hypothesis_horizons`로 분리한다.
- 구현 완료: `get_model_objective_profile()`로 단일 모델의 profile 복사본을 조회한다.
- 구현 완료: `build_model_objective_frame()`으로 공통 leaderboard와 선택 metric frame을 모델별 objective row로 요약한다.
- 구현 완료: objective frame에 `primary_hypothesis_horizons`, `record_all_horizons`, `best_horizon`을 남긴다.
- 구현 완료: metric 집계와 guardrail은 기본적으로 `primary_hypothesis_horizons` 기준으로 계산하고, `evaluated_horizons`와 `row_count`는 전체 horizon 기준으로 기록한다.
- 공통 leaderboard는 그대로 유지하고, objective frame은 모델별 목적 점수와 guardrail 통과 여부를 별도로 가진다.
- `objective_score`는 같은 모델/profile 내부 sweep 비교에만 사용하고, 서로 다른 모델의 objective score를 직접 비교하지 않는다.
- TCN은 high confidence precision만 높고 coverage가 너무 낮은 전략을 막기 위해 coverage 최소 guardrail을 둔다.
- profile이 없는 모델은 기본적으로 건너뛰며, `missing_model_policy="error"`를 쓰면 명확한 오류로 처리한다.

`AI/modules/signal/evaluation/runner.py`

- 구현 완료: `run_smoke_evaluation()`은 정규화된 signal frame과 realized forward return을 받아 schema 검증, 모델별 Top-k 백테스트, `universe_equal` 기준선, leaderboard 저장까지 한 번에 실행한다.
- 구현 완료: `run_smoke_evaluation()`은 백테스트 전 `build_signal_diagnostics_frame()`을 호출하고, 결과에 `diagnostics_frame`을 포함하며 `{run_id}_diagnostics.csv`를 저장한다.
- 구현 완료: `load_smoke_config()`는 기본 smoke 설정 위에 dict 또는 JSON 설정을 덮어쓴다.
- 구현 완료: `normalize_smoke_prediction_outputs()`는 저장된 wrapper 출력 record를 `normalize_signal_output()`으로 Signal Schema v0 frame으로 변환한다.
- v0 runner는 학습, 피처 생성, wrapper 내부 로직, MoE를 수행하지 않는다.
- `decision_time` 결측은 schema 검증에서 실패한다.
- 기본 horizon은 `[1, 3, 5, 7]`이며, `require_all_horizons=True`이면 signal과 realized return 모두 같은 horizon set을 가져야 한다.
- `require_all_horizons=False`이면 설정 horizon, signal horizon, realized return horizon의 교집합만 평가한다.
- `signal_frame`과 `prediction_outputs`는 동시에 넘길 수 없다. 이미 정규화된 평가 입력은 `signal_frame`, wrapper 출력 record smoke는 `prediction_outputs`를 사용한다.
- realized return에 `label_start_date` 또는 `label_end_date`가 있으면 label 기간이 `asof_date` 이전으로 새지 않는지 검증한다. `label_start_date == asof_date`는 asof 종가 기준 forward return 계산을 위해 허용하고, `label_end_date`는 반드시 `asof_date` 이후여야 한다. 컬럼이 없으면 runner는 forward return 값을 label로만 사용하고 feature 생성에는 쓰지 않는다.
- `prediction_status`는 기본 `include_statuses=("ok",)`만 평가에 포함하므로 fallback row는 기본 백테스트에서 제외된다.
- `missing_return_policy="error"`가 기본이고, `"drop"`은 실험용으로만 허용하며 leaderboard에 `missing_return_rate`를 남긴다.
- 저장 파일명에는 안전한 문자만 남기므로 `leaderboard_run_id`에 경로 문자가 들어와도 output directory 밖으로 벗어나지 않는다.
- 저장되는 signal frame, equity curve, trades csv는 재현 가능한 비교를 위해 주요 key 기준으로 정렬한다.

`AI/modules/signal/evaluation/feature_sets.py`

- `technical_daily_v1`: TCN 11개 피처.
- `tcn_short_horizon_v1`: TCN short return, candle, volume shock, short volatility 피처.
- `technical_mtf_v1`: Transformer/PatchTST 17개 현재 피처.
- `transformer_technical_market_v1`: Transformer baseline에 최소 market state를 더한 후보.
- `patchtst_swing_ranking_v1`: PatchTST용 rolling return, trend stack, volatility regime, sector relative strength 후보.
- `macro_corr_v1`: iTransformer 현재 macro/correlation 피처.
- `itransformer_regime_corr_v1`: iTransformer regime/correlation/risk-state 확장 후보.
- 각 feature set의 컬럼 순서와 생성 함수를 명시.

### 9.2 `model_manager.py` 연결

`initialize_models()`는 이미 wrapper 로딩과 scaler 로딩을 담당한다. 평가 파이프라인은 이 함수를 재사용할 수 있다.

필요한 최소 보강:

- 로드된 wrapper에서 `get_required_features()`를 읽어 schema의 `feature_count`, `feature_set_ver`와 비교한다.
- artifact 경로, scaler 경로, metadata 경로를 prediction row의 `model_ver` 또는 `artifact_path`로 남긴다.

### 9.3 `portfolio_logic.py` 연결

현재 `calculate_portfolio_allocation()`은 모델별 horizon 딕셔너리를 평균내 ticker score 하나로 만든다. 운영 포트폴리오에는 쓸 수 있지만 평가판에는 horizon별 정보가 사라지는 문제가 있다.

최소 연결 방향:

- 평가 파이프라인에서는 `portfolio_logic.py`를 직접 백테스트 엔진으로 쓰지 않는다.
- 대신 `portfolio_logic.py`의 top-k, threshold, default score 개념을 `evaluation/backtest.py`에 명시적으로 복제한다.
- 나중에 운영과 평가가 같은 signal schema를 읽도록 `portfolio_logic.py`에 adapter를 추가한다.

### 9.4 `test_ensemble_backtest.py` 연결

참고 가능한 부분:

- MDD, Sharpe, annual return 계산 아이디어.
- 모델별 score 추출과 단순 평균 ensemble 흐름.
- threshold 기반 buy/sell 흐름.

평가판에서 그대로 쓰기 어려운 부분:

- 로드 경로와 모델명 대소문자가 현재 artifact resolver와 다르다.
- same-day 체결과 lookahead 여부를 명확히 검증해야 한다.
- 5-fold가 모델 재학습 기반 OOF가 아니라 기간별 평가 스크립트에 가깝다.
- 파일이 `.gitignore` 대상이다.

### 9.5 `AI/tests/run_backtest.py`

현재 파일이 없으므로 연결 지점을 확인할 수 없다. 파일이 추가되면 evaluation 모듈의 smoke test 또는 CLI wrapper로 연결하는 것이 좋다.

## 10. 서버 크론잡 기준 자동화 방향

GitHub Actions는 설계 대상에서 제외한다.

서버 크론잡 v0 흐름:

1. 최신 price/macro/breadth 데이터를 적재한다.
2. 평가 대상 fold 또는 live asof date를 결정한다.
3. 모델 artifact를 로드한다.
4. `evaluation/walk_forward.py` 또는 live prediction runner가 signal schema v0 예측을 저장한다.
5. `evaluation/runner.py`가 작은 기간 smoke 평가에서 schema, backtest, leaderboard 연결을 검증한다.
6. `evaluation/backtest.py`가 신규 OOF 예측을 평가한다.
7. `evaluation/leaderboard.py`가 leaderboard를 갱신한다.
8. wandb 로그는 선택 사항이며 커밋하지 않는다.

권장 CLI 예시:

```bash
python -m AI.modules.signal.evaluation.run_walk_forward --config AI/config/signal_evaluation.json
python -m AI.modules.signal.evaluation.build_leaderboard --prediction-dir AI/backtests/results/signal_evaluation/predictions
```

## 11. MoE/Gating 로드맵

### v0: 단순 평균

필요 데이터:

- 모델별 OOF `prob_up`.
- 공통 `asof_date`, `ticker`, `horizon`.

누수 위험:

- 모델별 eval 기간이 다르면 평균 성과가 왜곡된다.
- 예측 실패 모델을 임의로 제외하면 좋은 모델만 남는 선택 편향이 생길 수 있다.

### v1: 모델별 고정 가중치

필요 데이터:

- 과거 OOF leaderboard.
- horizon별 primary metric 또는 net return.

가중치 예:

- Transformer 0.25, TCN 0.25, PatchTST 0.25, iTransformer 0.25에서 시작.
- train fold 성과 기준으로 horizon별 고정 가중치 산출.

누수 위험:

- 평가 fold 성과로 그 fold의 가중치를 정하면 누수다.
- 특정 기간 최고 모델에 과적합될 수 있다.

### v2: rule-based gating

필요 데이터:

- VIX, 금리 변화, breadth, recent_loss_ema, correlation_spike 등 decision time에 알 수 있는 regime feature.
- 모델별 OOF 성과의 regime별 집계.

규칙 예:

- high VIX와 breadth 악화 시 iTransformer 가중치 증가.
- 1일/3일 단기 변동성이 높을 때 TCN 가중치 증가.
- 추세가 명확하고 turnover 비용이 크면 PatchTST 5일/7일 가중치 증가.

누수 위험:

- regime 라벨을 미래 수익률로 정의하면 안 된다.
- rule threshold를 eval fold에서 튜닝하면 안 된다.

### v3: model_recent_loss_ema 기반 soft gating

필요 데이터:

- 모델별 OOF 예측 손실.
- `asof_date` 기준 과거 손실만으로 계산한 EMA.
- horizon별 최근 성능.

방식:

- 최근 손실 EMA가 낮은 모델의 가중치를 높인다.
- softmax 또는 inverse loss weighting을 사용한다.

누수 위험:

- 손실 EMA 계산에 현재 eval label을 포함하면 누수다.
- horizon label이 확정되기 전에는 해당 horizon loss를 업데이트하면 안 된다.

### v4: 학습형 meta model

필요 데이터:

- base model의 OOF `prob_up`, `confidence`.
- decision time regime feature.
- 과거 fold의 realized label 또는 forward return.

방식:

- meta model은 base model의 OOF 예측만 입력으로 학습한다.
- 최종 live/holdout 평가에는 meta model 학습에 쓰지 않은 fold만 사용한다.

누수 위험:

- base model in-sample 예측을 meta model 학습에 쓰는 stacking leakage.
- feature scaler와 meta model threshold를 전체 데이터로 fit하는 leakage.
- 종목 universe 생존 편향.

## 12. 확인 필요 사항

우선순위 높은 항목:

- `AI/tests/run_backtest.py`가 현재 없다. 별도 브랜치나 생성 예정 파일인지 확인 필요.
- `PatchTST` 디렉터리 대소문자와 import 경로 `patchtst` 불일치 확인 필요.
- Transformer 로컬 학습의 scaler 전체 fit과 shuffle split은 평가용으로는 부적절하다. walk-forward 평가에서는 별도 데이터 생성 경로를 써야 한다.
- PatchTST의 누락 피처 처리 규칙을 학습, 평가, 운영으로 분리해야 한다. 평가에서는 fallback을 metric에 표시해야 한다.
- Transformer와 PatchTST artifact에는 metadata가 부족하다. 최소한 feature list, horizon, seq_len, train window를 별도 sidecar json으로 남기는 것이 좋다.
- `portfolio_logic.py`는 horizon별 정보를 평균내므로 모델 목적별 평가에는 직접 쓰기 어렵다.

차후 구현 시 먼저 만들 최소 단위:

- `schema.py`: wrapper 출력 정규화.
- `metrics.py`: classification/ranking/portfolio/calibration 지표.
- `backtest.py`: signal frame 기반 top-k 백테스트.
- `leaderboard.py`: leaderboard csv 생성.
- `objectives.py`: 모델별 목적 profile 성적표 생성.
- `runner.py`: smoke evaluation end-to-end 연결 검증.

이 파일들만 있어도 기존 학습 코드를 크게 건드리지 않고 평가판 v0를 시작할 수 있다.

## 13. PatchTST/iTransformer feature audit v1

이번 감사는 학습이나 sweep을 실행하지 않고 코드 기준 입력 계약만 확인했다. 모든 모델은 계속 `[1, 3, 5, 7]` horizon 출력을 유지하고, 모델별 강점은 horizon 고정보다 feature set과 구조적 역할로 구분한다.

### 13.1 PatchTST 현재 상태

확인 파일:

- `AI/modules/signal/models/PatchTST/architecture.py`
- `AI/modules/signal/models/PatchTST/train.py`
- `AI/modules/signal/models/PatchTST/train_kaggle.py`
- `AI/modules/signal/models/PatchTST/wrapper.py`

현재 입력 구조:

- 구조는 RevIN, channel-independent patch embedding, Transformer encoder, MLP head 기반이다.
- 기본 설정은 `seq_len=120`, `patch_len=16`, `stride=8`, `enc_in=17`, `horizons=[1, 3, 5, 7]`이다.
- 현재 feature는 일봉 11개, 주봉 4개, 월봉 2개로 구성된다.
- 현재 feature 목록은 `log_return`, `ma5_ratio`, `ma20_ratio`, `ma60_ratio`, `rsi`, `bb_position`, `macd_ratio`, `open_ratio`, `high_ratio`, `low_ratio`, `vol_change`, `week_ma20_ratio`, `week_rsi`, `week_bb_pos`, `week_vol_change`, `month_ma12_ratio`, `month_rsi`이다.

학습/추론 계약:

- `train.py`, `train_kaggle.py`, `wrapper.py`의 feature 순서는 현재 동일하다.
- local `train.py`는 누락 feature가 있으면 경고만 내고 존재하는 feature로 시퀀스를 만들 수 있다.
- `train_kaggle.py`는 누락 feature를 `ValueError`로 처리한다.
- `wrapper.py`는 누락 feature를 `0.0`으로 채우고, 모델 또는 scaler가 없거나 데이터가 부족하면 `0.5` 기본값을 반환한다.
- 별도 `metadata.json`이 없고, checkpoint 내부 `config`와 scaler pickle만 저장한다.
- `feature_set_ver`, `feature_columns`, `feature_count`, `train_window`, `eval_window`를 평가 schema에 안정적으로 넘기기 어렵다.

목적성 관점 평가:

- PatchTST 구조는 patch 단위로 긴 구간의 반복 패턴과 swing/ranking 정보를 보는 데 적합하다.
- 현재 feature는 이동평균, RSI, Bollinger, MACD, 주봉/월봉 보조지표가 있어 다중 시간축 technical baseline으로는 쓸 수 있다.
- 다만 ranking expert 목적에 필요한 rolling return stack, volatility regime, sector relative strength는 부족하다.
- 현재 feature에는 `rolling_return_5`, `rolling_return_10`, `rolling_return_20`, `atr_14` 또는 rolling volatility, volume trend, `sector_return_rel`, `days_since_earnings` 계열이 명시적으로 없다.

권장 feature_set_ver:

- 현재 baseline은 `patchtst_technical_mtf_v1`로 명명한다.
- 다음 목적형 후보는 `patchtst_swing_ranking_v1`로 둔다.

`patchtst_swing_ranking_v1` 후보 feature:

- `log_return`
- `rolling_return_5`, `rolling_return_10`, `rolling_return_20`
- `ma5_ratio`, `ma20_ratio`, `ma60_ratio`
- `atr_14` 또는 `rolling_vol_10`, `rolling_vol_20`
- `volume_trend_5`, `volume_trend_20`
- `sector_return_rel`
- `week_ma20_ratio`, `week_rsi`, `week_bb_pos`, `week_vol_change`
- `month_ma12_ratio`, `month_rsi`
- `days_since_earnings`는 데이터 안정성이 확인될 때만 추가한다.

PatchTST 우선 수정 범위:

1. `train.py`, `train_kaggle.py`, `wrapper.py`에 공통 feature resolver를 두고 누락 feature 처리 규칙을 통일한다.
2. 평가 경로에서는 누락 feature를 0으로 채우지 않고 오류 또는 `prediction_status="fallback"`으로 기록한다.
3. `metadata.json` 또는 checkpoint sidecar에 `feature_set_ver`, `feature_columns`, `feature_count`, `seq_len`, `patch_len`, `stride`, `horizons`를 저장한다.
4. wrapper는 metadata의 feature 순서를 우선 사용한다.
5. 이후 작은 smoke에서 diagnostics, leaderboard, objective frame을 생성한다.

### 13.2 iTransformer 현재 상태

확인 파일:

- `AI/modules/signal/models/itransformer/architecture.py`
- `AI/modules/signal/models/itransformer/train.py`
- `AI/modules/signal/models/itransformer/train_kaggle.py`
- `AI/modules/signal/models/itransformer/wrapper.py`
- `AI/modules/features/market_derived.py`
- `AI/modules/features/processor.py`
- `AI/modules/signal/core/data_loader.py`

현재 입력 구조:

- 구조는 `[batch, seq_len, feature]` 입력을 `[batch, feature, seq_len]`로 뒤집어 feature 축을 token처럼 attention하는 방식이다.
- 기본 설정은 local 기준 `lookback=60`, `horizons=[1, 3, 5, 7]`, `head_size=128`, `num_heads=4`, `num_blocks=4`이다.
- ticker embedding과 sector embedding을 함께 사용한다.

현재 local 후보 feature:

- `us10y`
- `us10y_chg`
- `yield_spread`
- `vix_close`
- `vix_change_rate`
- `dxy_close`
- `dxy_chg`
- `credit_spread_hy`
- `wti_price`
- `gold_price`
- `nh_nl_index`
- `ma200_pct`
- `correlation_spike`
- `recent_loss_ema`
- `ret_1d`
- `intraday_vol`
- `log_return`
- `surprise_cpi`

추가 context 후보:

- `btc_close`, `eth_close`는 존재하면 추가된다.
- `sector_return_` prefix feature는 존재하면 동적으로 추가된다.

학습/추론 계약:

- local `train.py`는 명시 `feature_columns`가 있으면 누락 시 `ValueError`를 낸다.
- 명시 feature가 없으면 후보 중 실제 존재하는 컬럼만 선택하고, 최소 feature 수가 부족하면 `ValueError`를 낸다.
- local `train.py`는 날짜 기준 time split을 사용하고 scaler를 train 구간에만 fit한다.
- local metadata에는 `feature_names`, `feature_focus`, `horizons`, `seq_len`, ticker/sector mapping, 구조 파라미터가 저장된다.
- `train_kaggle.py`도 `feature_names`, `feature_columns`를 metadata에 저장하지만, local처럼 optional context와 dynamic sector feature resolver를 완전히 공유하지는 않는다.
- wrapper는 metadata 또는 scaler의 feature 순서를 복원하고, 누락 feature는 `ValueError`로 처리한다.
- 현재 metadata에는 `feature_set_ver`가 없다.

목적성 관점 평가:

- iTransformer는 regime/correlation/risk-state expert 목적에 비교적 맞는 입력을 이미 받고 있다.
- 금리, VIX, DXY, credit spread, WTI, gold, breadth, correlation spike, recent loss 계열이 포함되어 있어 risk-off와 macro 상태를 일부 볼 수 있다.
- 다만 `us2y` 원천값은 명시 feature가 아니고 `yield_spread`로만 들어간다.
- `btc_close`, `eth_close`, `sector_return_*`는 optional 또는 dynamic이라 artifact마다 feature 수가 달라질 수 있다.
- `credit_spread_hy`, `surprise_cpi`, breadth 계열이 실제 모든 실행 환경에서 안정적으로 채워지는지 별도 smoke가 필요하다.

권장 feature_set_ver:

- 현재 baseline은 `macro_corr_v1`로 명명한다.
- 다음 목적형 후보는 `itransformer_regime_corr_v1`로 둔다.

`itransformer_regime_corr_v1` 후보 feature:

- 현재 local 후보 feature 전체
- `us2y`
- `yield_spread`
- `vix_close`, `vix_change_rate`
- `dxy_close`, `dxy_chg`
- `credit_spread_hy`
- `wti_price`, `gold_price`
- `btc_close`, `eth_close`
- `sector_return_*`
- `nh_nl_index`, `ma200_pct`
- `correlation_spike`, `recent_loss_ema`
- event 또는 surprise 계열은 데이터 안정성이 확인될 때만 추가한다.

iTransformer 우선 수정 범위:

1. local/Kaggle 공통 feature resolver를 분리해 같은 feature 선택 규칙을 사용한다.
2. metadata에 `feature_set_ver`, `feature_columns`, `feature_count`를 명시 저장한다.
3. optional feature가 들어간 artifact는 signal schema의 `feature_set_ver`와 `feature_count`로 반드시 구분한다.
4. 평가에서는 missing feature를 0으로 채우지 않고 실패 처리한다. 현재 wrapper는 이 원칙에 대체로 맞다.
5. feature availability smoke로 `credit_spread_hy`, `surprise_cpi`, `sector_return_*`, `btc_close`, `eth_close` 실제 존재율을 먼저 확인한다.

### 13.3 공통 구현 우선순위

1. PatchTST의 silent fallback을 먼저 제거하거나 평가용 wrapper adapter에서 `prediction_status="fallback"`으로 분리한다.
2. PatchTST에 metadata sidecar를 추가한다.
3. iTransformer에 `feature_set_ver`와 `feature_count` metadata를 추가한다.
4. 두 모델 모두 학습/추론 feature resolver를 공통 함수로 묶는다.
5. 작은 ticker/date subset으로 diagnostics csv를 먼저 생성하고, fallback rate와 missing feature를 확인한 뒤 leaderboard/objective를 본다.

### 13.4 smoke 전 보류 사항

- PatchTST의 `days_since_earnings`는 earnings 데이터 안정성이 확인되기 전까지 보류한다.
- iTransformer의 crypto, sector relative feature는 local DB와 Kaggle parquet 양쪽 가용성을 확인한 뒤 feature set에 고정한다.
- feature가 없을 때 0으로 채우는 방식은 평가에서는 금지한다. 운영 fallback이 필요하면 signal schema에 `prediction_status="fallback"`으로 남긴다.
- 특정 horizon 성능이 예상과 다르게 나오더라도 출력 horizon은 줄이지 않는다. 모든 horizon을 기록하고 objective frame에서 주력 가설 horizon만 따로 해석한다.

### 13.5 PatchTST 평가 경로 fallback/metadata 정리 v1

구현 파일:

- `AI/modules/signal/models/PatchTST/feature_contract.py`
- `AI/modules/signal/models/PatchTST/train.py`
- `AI/modules/signal/models/PatchTST/train_kaggle.py`
- `AI/modules/signal/models/PatchTST/wrapper.py`

metadata sidecar 계약:

- 파일명 기본값은 `metadata.json`이다.
- 필수 필드는 `feature_set_ver`, `feature_columns`, `feature_count`, `seq_len`, `patch_len`, `stride`, `horizons`, `scaler_path`, `model_path`이다.
- 현재 baseline `feature_set_ver`는 `patchtst_technical_mtf_v1`이다.
- `feature_count`는 `feature_columns` 길이와 반드시 같아야 한다.
- wrapper는 metadata가 있으면 metadata의 feature 순서, seq_len, horizon, patch 설정을 우선한다.
- metadata가 없으면 legacy artifact로 로드하되 `legacy_artifact=True`, `artifact_status="legacy"` 상태가 남는다.
- legacy artifact에서 정상 추론이 가능해도 `predict_with_status()`는 `prediction_status="fallback"`을 반환한다. metadata 계약이 없는 예측은 기본 평가 집계에서 제외한다.
- metadata와 checkpoint config의 `seq_len`, `patch_len`, `stride`, `horizons`, `feature_columns`가 다르면 로드 단계에서 실패한다.

fallback 처리:

- wrapper는 더 이상 누락 feature를 조용히 `0.0`으로 채우지 않는다.
- `predict()`는 기존 호출 호환을 위해 확률 dict만 반환한다.
- 평가 경로는 `predict_with_status()`를 사용한다.
- `predict_with_status()`는 정상 예측이면 `prediction_status="ok"`를 반환한다.
- 모델, scaler, 입력 길이, 누락 feature, NaN/inf 등으로 추론할 수 없으면 0.5 중립 확률을 반환하되 `prediction_status="fallback"`과 `error_message`를 함께 반환한다.
- runner에 넘길 때는 반환 record의 `prediction_status`, `error_message`, `feature_set_ver`, `seq_len`, `feature_count`, `artifact_path`를 `normalize_signal_output()`에 그대로 전달한다.

학습 경로 정리:

- local `train.py`와 Kaggle `train_kaggle.py`는 `feature_contract.py`의 `PATCHTST_FEATURE_COLUMNS`를 실제 feature source of truth로 사용한다.
- local 학습도 누락 feature를 경고 후 진행하지 않고 `ValueError`로 실패한다.
- 학습 종료 후 model/scaler와 같은 디렉터리에 metadata sidecar를 저장한다.

평가 해석:

- metadata 없는 기존 artifact는 평가에서 legacy로 분리해 기록해야 한다.
- fallback 0.5 row는 정상 예측이 아니므로 leaderboard/backtest 기본 설정의 `include_statuses=("ok",)`에서는 제외된다.
- fallback을 포함한 실험이 필요하면 호출자가 명시적으로 `include_statuses=("ok", "fallback")`을 지정한다.

### 13.6 Model Metric Frame Builder v1

구현 파일:

- `AI/modules/signal/evaluation/model_metrics.py`
- `AI/modules/signal/evaluation/runner.py`
- `AI/modules/signal/evaluation/__init__.py`

목적:

- 공통 signal frame, realized return, leaderboard/backtest 결과를 모델별 objective에 필요한 `metric_frame`으로 자동 변환한다.
- `objectives.py`는 기존처럼 `metric_frame` 선택 입력을 받되, runner에서는 이를 자동으로 생성해 `build_model_objective_frame()`에 넘긴다.

생성 지표:

- Transformer: horizon별 `brier_score`, `log_loss`, `ece`, `accuracy`.
- TCN: horizon별 `high_confidence_precision`, `high_confidence_coverage`, `high_confidence_accuracy`.
- PatchTST: horizon별 `rank_ic_mean`, `top_bottom_spread`, `top_k_mean_return`.
- iTransformer: horizon별 `net_return`, `mdd`, `calmar`, `downside_return`.

### 13.7 iTransformer metadata/feature contract 정리 v1

구현 파일:

- `AI/modules/signal/models/itransformer/feature_contract.py`
- `AI/modules/signal/models/itransformer/train.py`
- `AI/modules/signal/models/itransformer/train_kaggle.py`
- `AI/modules/signal/models/itransformer/wrapper.py`

feature contract:

- 현재 baseline feature set version은 `itransformer_regime_corr_v1`이다.
- 기본 피처는 금리, 달러, 변동성, 원자재, breadth, correlation/risk proxy, 최소 가격 context를 포함한다.
- `btc_close`, `eth_close`, `sector_return_*`는 optional/dynamic context로 두며, 실제 컬럼이 있는 artifact는 `feature_columns`와 `feature_count`로 구분한다.
- 명시 `feature_columns`가 있으면 누락 피처를 허용하지 않는다. 평가 경로에서는 누락 피처를 0으로 채우지 않는다.

metadata sidecar 계약:

- 파일명 기본값은 `metadata.json`이다.
- 필수 필드는 `feature_set_ver`, `feature_columns`, `feature_count`, `seq_len`, `horizons`, `scaler_path`, `model_path`이다.
- local `train.py`와 Kaggle `train_kaggle.py`는 공통 `feature_contract.py` resolver를 사용한다.
- wrapper는 metadata의 feature 순서와 scaler의 `feature_names_in_` 또는 `n_features_in_`가 다르면 실패한다.

fallback 처리:

- metadata가 없거나 구버전 metadata가 계약을 만족하지 않으면 legacy artifact로 표시한다.
- legacy artifact에서 예측이 가능해도 `predict_with_status()`는 `prediction_status="fallback"`을 반환한다.
- 모델, scaler, 누락 피처, feature 순서 오류는 fallback record로 분리하고, 기본 backtest/leaderboard는 `include_statuses=("ok",)` 기준으로 제외한다.

공통 컬럼:

- `model_name`
- `horizon`
- `run_id`
- `leaderboard_run_id`
- `metric_source`
- `count_rows`
- `missing_return_count`
- `missing_return_rate`

runner 연결:

- `run_smoke_evaluation()` 결과에 `metric_frame`과 `objective_frame`을 포함한다.
- `{run_id}_metric_frame.csv`와 `{run_id}_objective_frame.csv`를 저장한다.
- 기본 metric 계산은 `prediction_status="ok"` row만 사용한다.
- fallback row를 실험에 포함하려면 `include_statuses`를 명시해야 한다.

보류:

- iTransformer의 regime별 downside/risk-off metric은 아직 구현하지 않는다.
- regime 데이터가 안정화되면 `metric_source="regime"` row를 추가하는 방식으로 확장한다.
## 평가 파이프라인 방어선 리뷰 v1 메모

- backtest metrics는 `selected_periods`, `total_periods`, `avg_selected_count`, `cash_period_rate`, `all_cash_periods`를 함께 남긴다. 이 값은 `net_return=0`이 실제 손익 0인지, 후보가 없어 전 기간 현금이었는지 구분하기 위한 최소 방어선이다.
- leaderboard는 전 기간 현금 또는 누락 forward return이 있으면 `note`에 자동 진단 메모를 남긴다. `net_return`만 보고 무효에 가까운 전략을 좋은 전략으로 오해하지 않게 하기 위함이다.
- objective guardrail은 `cash_period_rate` 최대값을 확인한다. 전 기간 현금에 가까운 row는 `guardrail_pass=False`와 사유로 드러나야 한다.
- `label positive rate`와 `prob_up` 분포 쏠림 진단은 공통 runner의 `diagnostics_frame`과 diagnostics csv에 기록한다.
