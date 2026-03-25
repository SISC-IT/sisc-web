# 거래봇 설정 가이드

## 파일 구성

- `AI/config/trading.default.json`: 팀 공통 기본 설정 파일입니다.
- `AI/config/trading.local.json`: 로컬 전용 override 파일입니다. 존재하면 자동으로 읽습니다.
- `AI_TRADING_CONFIG_PATH`: 추가 override 파일 경로를 지정하는 환경 변수입니다.

설정 적용 순서는 아래와 같습니다.

1. `trading.default.json`
2. `trading.local.json`
3. `AI_TRADING_CONFIG_PATH`
4. CLI `--config`

뒤에 오는 설정이 앞선 값을 덮어씁니다.

## 설정 항목 요약

| 경로 | 기본값 | 의미 | 영향 범위 |
| --- | --- | --- | --- |
| `pipeline.db_name` | `db` | 사용할 DB 연결 이름 | 어떤 포트폴리오/시세 데이터를 읽고 저장하는지 바뀝니다 |
| `pipeline.default_mode` | `simulation` | CLI 기본 실행 모드 | 시뮬레이션/라이브 분기 기본값이 바뀝니다 |
| `pipeline.enable_xai` | `true` | XAI 리포트 기본 활성화 여부 | 실행 시간, API 사용량, XAI 저장 여부에 영향이 있습니다 |
| `pipeline.data_start_date` | `2023-01-01` | 추론 시 불러올 최소 시작일 | 종목별 사용 가능한 히스토리 길이가 달라집니다 |
| `pipeline.initial_capital` | `10000` | 포트폴리오 요약이 없을 때 쓰는 초기 자본 | 현금 fallback, 포지션 크기, 수익률 기준이 달라집니다 |
| `pipeline.active_models` | `["transformer"]` | `--models`를 생략했을 때 쓸 기본 모델 목록 | 어떤 가중치 파일을 읽고 어떤 신호를 합성할지 바뀝니다 |
| `pipeline.macro_fallback.*` | `0.0 / 0.0 / 0.5` | 매크로 입력이 없을 때 쓰는 더미 시장 상태값 | 게이팅/리스크 오버레이 fallback 판단이 달라집니다 |
| `screener.top_n` | `30` | 스크리너가 뽑는 후보 종목 수 | 감시 종목 수와 포트폴리오 후보 폭이 달라집니다 |
| `screener.lookback_days` | `10` | 거래대금 평균 계산 기간 | 유동성 필터가 최근 흐름에 얼마나 민감한지 달라집니다 |
| `screener.min_market_cap` | `10000000000` | 최소 시가총액 | 투자 가능 유니버스와 변동성이 달라집니다 |
| `screener.watchlist_path` | `AI/config/watchlist.json` | 최신 워치리스트 저장 위치 | 스크리너 결과가 기록되는 파일 위치가 바뀝니다 |
| `data.seq_len` | `60` | 모델 입력 시퀀스 길이 | 모델 추론 입력 형태와 직접 연결됩니다 |
| `data.minimum_history_length` | `60` | 전처리 후 최소 데이터 길이 | 히스토리가 짧은 종목을 얼마나 빨리 제외할지 달라집니다 |
| `data.feature_columns` | `[]` | 모델 메타데이터를 복구하지 못할 때 쓸 fallback feature 목록 | wrapper feature 복구 실패 시 전처리 동작이 달라집니다 |
| `data.prediction_horizons` | `[1, 3, 5, 7]` | loader horizon 기본값 | 같은 config를 재사용하는 학습/추론 워크플로우에도 영향을 줍니다 |
| `model.weights_dir` | `AI/data/weights` | 모델 아티팩트 루트 경로 | 어떤 모델 폴더를 기준으로 가중치/스케일러를 찾는지 바뀝니다 |
| `model.weights_file` | `tests/multi_horizon_model_test{ext}` | 모델별 상대 가중치 파일 패턴 | 모델 확장자별 실제 로딩 파일이 달라집니다 |
| `model.scaler_file` | `tests/multi_horizon_scaler_test.pkl` | 모델별 상대 스케일러 파일 경로 | feature 스키마 복구 기준이 달라집니다 |
| `portfolio.top_k` | `3` | 최대 보유 종목 수 | 포트폴리오 집중도가 달라집니다 |
| `portfolio.buy_threshold` | `0.7` | 포트폴리오 편입 최소 점수 | 매수 진입 강도가 달라집니다 |
| `portfolio.default_score` | `0.5` | 모델 실패 시 쓰는 중립 점수 | 신호 누락/오류 상황에서의 기본 동작이 달라집니다 |
| `portfolio.risk_overlay.vix_reduce_exposure_threshold` | `2.0` | 1차 변동성 브레이크 기준 | 변동성 상승 시 감산이 시작되는 시점이 달라집니다 |
| `portfolio.risk_overlay.vix_exit_threshold` | `3.0` | 강제 방어 기준 | 전량 현금화 수준 방어가 시작되는 시점이 달라집니다 |
| `portfolio.risk_overlay.reduced_exposure_ratio` | `0.5` | 1차 브레이크 이후 노출 비율 | 고변동성 구간에서 목표 비중 축소 폭이 달라집니다 |
| `portfolio.risk_overlay.full_exit_ratio` | `0.0` | 강제 방어 이후 노출 비율 | 극단적 방어 구간에서 잔여 익스포저가 달라집니다 |
| `execution.strong_buy_score` | `0.8` | 최대 conviction으로 보는 점수 | 풀사이징 도달 시점이 달라집니다 |
| `execution.buy_score_floor` | `0.7` | 신규 매수 허용 하한 점수 | conviction ramp 시작점이 달라집니다 |
| `execution.sell_score` | `0.5` | 전량 매도 기준 점수 | 모멘텀 이탈에 대한 방어 민감도가 달라집니다 |
| `execution.stop_loss_ratio` | `0.07` | 손절 기준 손실률 | 강제 청산 시점이 달라집니다 |
| `execution.min_conviction_weight` | `0.3` | ramp 구간 최소 비중 | 조건 충족 직후 최소 진입 크기가 달라집니다 |
| `execution.max_conviction_weight` | `1.0` | ramp 구간 최대 비중 | conviction sizing 상한이 달라집니다 |
| `execution.commission` | `0.0` | 체결 수수료율 | 주문 가능 수량, 현금, 실현손익 계산이 달라집니다 |

## 운영 가이드

- 공통 기준은 `trading.default.json`에 두고, 팀이 합의한 값만 유지합니다.
- 개인 실험이나 서버별 차이는 `trading.local.json` 또는 별도 `--config` 파일로 분리합니다.
- 튜닝은 한 섹션씩 나눠서 진행하는 것이 좋습니다. `portfolio`, `execution`이 체감 변화가 가장 큽니다.
- `data.seq_len`, `data.feature_columns`, 모델 파일 경로는 전략값이라기보다 호환성 값에 가깝습니다. 학습 산출물과 어긋나면 추론이 깨질 수 있습니다.
- 비교 실험은 파일을 나눠서 남겨야 재현성이 좋아집니다.

## 예시 Override

```json
{
  "portfolio": {
    "top_k": 5,
    "buy_threshold": 0.74
  },
  "execution": {
    "stop_loss_ratio": 0.05,
    "commission": 0.001
  }
}
```
