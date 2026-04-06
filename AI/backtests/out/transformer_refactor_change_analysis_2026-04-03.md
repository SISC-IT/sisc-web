# Transformer 변경점 심층 분석 (기준: 2026-04-03)

## 1) 분석 범위
- 비교 기준: `20260314 #307 daily routine 코드 리펙토링` (merge commit `5d752d2`) 전후 + 이후 관련 커밋
- 확인 파일:
  - `AI/modules/signal/models/transformer/train.py`
  - `AI/modules/signal/core/data_loader.py`
  - `AI/modules/signal/models/transformer/wrapper.py`
  - `AI/pipelines/components/model_manager.py`
  - `AI/modules/signal/core/artifact_paths.py`
  - `AI/modules/finder/screener.py`

## 2) 핵심 타임라인 (Transformer 관점)
- `6fd3b39` (`#307` 브랜치 내부):  
  - `train.py` import 경로 변경  
    - `PatchTST.architecture` -> `transformer.architecture`
  - 기본 저장 경로를 `.../transformer/tests/`로 변경
- `c9ed165` (`#307` 브랜치 내부):  
  - `TRANSFORMER_TRAIN_FEATURES` 17개 고정 도입
  - `create_dataset(..., feature_columns=TRANSFORMER_TRAIN_FEATURES)` 도입
  - `DataLoader`에서 `add_multi_timeframe_features()` 실제 호출 시작
- `867999e` (`#307` 브랜치 내부):  
  - wrapper에 입력 길이 검증 추가 (`len(df) < seq_len` 방어)
- `5d752d2` (main에 squash merge): 위 3개 변화가 사실상 반영
- `96a4640`:
  - config 구조화(`trading.py`, `trading.default.json`)
- `ae48e58`:
  - legacy h5 체크포인트의 input shape 추론 로직 추가
- `92bc3b3`:
  - artifact path 해석 로직(`artifact_paths.py`) 도입
  - `AI_MODEL_WEIGHTS_DIR` 환경변수 기반 아티팩트 루트 전환 가능

## 3) 피처 스키마 변화 (가장 큰 변화)

### 이전(동적 후보, `#307` 직전 `5d752d2^`)
`data_loader.py`의 `potential_features` 23개:
- `log_return, open_ratio, high_ratio, low_ratio, vol_change`
- `ma_5_ratio, ma_20_ratio, ma_60_ratio`
- `rsi, macd_ratio, bb_position`
- `us10y, yield_spread, vix_close, dxy_close, credit_spread_hy`
- `nh_nl_index, ma200_pct`
- `sentiment_score, risk_keyword_cnt`
- `per, pbr, roe`

### 현재(고정 17, `train.py`)
- `log_return, open_ratio, high_ratio, low_ratio, vol_change`
- `ma5_ratio, ma20_ratio, ma60_ratio`
- `rsi, macd_ratio, bb_position`
- `week_ma20_ratio, week_rsi, week_bb_pos, week_vol_change`
- `month_ma12_ratio, month_rsi`

### Set 비교
- 공통 8개:
  - `log_return, open_ratio, high_ratio, low_ratio, vol_change, rsi, macd_ratio, bb_position`
- 이전 대비 제거 15개:
  - `ma_5_ratio, ma_20_ratio, ma_60_ratio`
  - `us10y, yield_spread, vix_close, dxy_close, credit_spread_hy`
  - `nh_nl_index, ma200_pct`
  - `sentiment_score, risk_keyword_cnt`
  - `per, pbr, roe`
- 현재에서 신규 9개:
  - `ma5_ratio, ma20_ratio, ma60_ratio`
  - `week_ma20_ratio, week_rsi, week_bb_pos, week_vol_change`
  - `month_ma12_ratio, month_rsi`

## 4) 중요한 정합성 포인트

### (A) MA 피처 네이밍 불일치 이슈의 구조
- `add_technical_indicators()`는 `ma{window}_ratio` (예: `ma5_ratio`) 생성
- 과거 동적 후보에는 `ma_5_ratio` 형태가 들어가 있어, 동적 경로에서는 MA 3개가 빠질 여지가 있었음
- `#307` 이후 고정 17로 `ma5_ratio`를 직접 지정하면서 이 불일치가 실질 해소됨

### (B) 멀티타임프레임 피처의 “정의는 있었지만 사용은 안 됨” -> “사용 시작”
- `add_multi_timeframe_features()` 함수 자체는 이전에도 존재
- 다만 `DataLoader.create_dataset()`에서 실제 호출된 것은 `#307` 라인에서 시작
- 즉, 주봉/월봉 6개 피처는 `#307` 이후 학습 입력에 본격 반영

### (C) 아티팩트 경로/로드 방식 변화
- `#307` 이후 + 후속 커밋에서 아래가 바뀜:
  - 테스트/프로덕션 파일명 분기 (`*_test`, `*_prod`)
  - 환경변수 기반 weights 루트 전환
  - legacy `.keras`(실제 h5) 로드 폴백 강화
- 따라서 “같은 코드라도 어떤 체크포인트를 읽었는지”가 결과에 크게 영향 가능

## 5) 현재 실제 스케일러 상태 확인 (로컬 파일 기반)

확인 파일:
- `AI/data/weights/transformer/tests/multi_horizon_scaler_test.pkl`
- `AI/data/weights/transformer/prod/multi_horizon_scaler_prod.pkl`

공통 결과:
- `n_features_in_ = 17`
- `feature_names_in_`가 현재 고정 17과 동일

관찰 포인트:
- `vol_change`, `week_vol_change`의 max가 매우 큼 (긴 꼬리)
  - test: `vol_change max ~15420`, `week_vol_change max ~25361`
  - prod: `vol_change max ~15420`, `week_vol_change max ~120508`
- MinMaxScaler 사용 시 극단치가 있으면 대부분 샘플이 좁은 구간으로 압축될 수 있음

## 6) “val_loss=0.690 정체”에 대한 코드 관점 해석
- BCE에서 `0.690~0.693` 고정은 보통 확률 0.5 근처(랜덤 수준) 신호
- 이번 코드 이력에서 그럴 만한 강한 변화는 아래 3가지:
  1. 입력 스키마가 사실상 재정의됨(23 동적 혼합 -> 17 고정 기술/멀티타임프레임)
  2. MA 네이밍 정합성 정리로 실제 입력 컬럼 조합이 바뀜
  3. 체크포인트/스케일러 경로 분기 변경으로 다른 아티팩트를 읽었을 가능성

즉, “단순 리팩토링”이 아니라 **학습 입력 분포와 로딩 경로가 동시에 바뀐 리팩토링**에 가까움.

## 7) 추가 확인 시도 결과
- DB 기반 재현 실행은 현재 환경에서 실패:
  - `localhost:15432` PostgreSQL 연결 거부
- 따라서 샘플 수/실제 선택 컬럼/라벨 분포를 런타임으로 재현하지는 못했고, 코드/커밋/스케일러 파일 단서 중심으로 분석함.

## 8) 스크리너 확장 관련 별도 관찰 (현재 코드)
- `AI/config/trading.py`에 확장 필드(`include_tickers`, `exclude_tickers`, `sticky_slots`, 가중치 등)는 존재
- `AI/modules/finder/screener.py`의 쿼리에는 아직 미반영
- 따라서 “GEV 같은 특정 티커 보존/우선” 요구는 현재 config만으로는 완전 제어 불가

