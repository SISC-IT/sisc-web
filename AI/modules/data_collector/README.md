# Data Collector

`data_collector`는 AI/트레이딩 파이프라인이 사용하는 원천 데이터를 수집하는 작업 공간입니다. 국내 주식 일봉 OHLCV 수집은 `components/korea_stock_data.py`와 `scripts/collect_korea_stocks.py`에서 담당합니다.

## 국내 주식 OHLCV 수집

- 기본 소스: `FinanceDataReader`
- 보조 소스: `pykrx`
- 대상 시장: `KOSPI`, `KOSDAQ`
- 표준 컬럼: `date`, `ticker`, `open`, `high`, `low`, `close`, `volume`, `trading_value`
- DB 호환 컬럼: 기존 `price_data.amount`와 맞추기 위해 `trading_value`를 `amount`에도 동일 저장
- 기본 저장: DB와 파일 모두 저장
- 기본 파일 포맷: CSV

현재 구현은 별도 API 키가 필요 없는 공개 데이터 수집 라이브러리를 사용합니다. 증권사 주문/시세 API가 필요해지는 실시간 또는 분봉 수집 단계에서만 계정/API 키가 필요합니다.

`pykrx`는 최신 버전이 `numpy>=2`를 요구할 수 있어, 이 프로젝트의 `numpy<2.0` 제약과 충돌할 수 있습니다. 그래서 기본 설치/실행은 `FinanceDataReader`로 맞추고, `pykrx`는 별도 수집 환경에서 선택적으로 사용하는 것을 권장합니다.

## 설치

```bash
pip install -r AI/requirements.txt
```

## 실행 예시

샘플 종목을 CSV로만 수집:

```bash
python AI/modules/data_collector/scripts/collect_korea_stocks.py --tickers 005930 000660 --start 2024-01-01 --end 2024-01-31 --storage file
```

KOSPI/KOSDAQ 전체를 DB와 파일에 저장:

```bash
python AI/modules/data_collector/scripts/collect_korea_stocks.py --markets KOSPI KOSDAQ --start 2015-01-01 --storage both
```

pykrx로 수집:

```bash
python AI/modules/data_collector/scripts/collect_korea_stocks.py --source pykrx --tickers 005930 --storage file
```

테스트용으로 앞 5개 종목만 수집:

```bash
python AI/modules/data_collector/scripts/collect_korea_stocks.py --markets KOSPI --limit 5 --start 2024-01-01 --end 2024-01-10 --storage file
```

## 검증

네트워크와 DB 없이 정규화 및 배치 오류 격리 동작을 검증:

```bash
python AI/tests/verify_korea_stock_data.py -v
```

삼성전자 한 종목을 실제로 수집해 CSV 저장을 검증:

```bash
python AI/modules/data_collector/scripts/collect_korea_stocks.py --tickers 005930 --markets KOSPI --start 2024-01-01 --end 2024-01-10 --storage file --sleep 0
```

## 디렉터리 구조

```text
AI/modules/data_collector/
  components/
    korea_stock_data.py
  config/
    korea_stocks.json
  logs/
    failed_tickers_YYYYMMDD_HHMMSS.csv
    korea_stock_data_YYYYMMDD.log
  scripts/
    collect_korea_stocks.py
  storage/
    korea_ohlcv/
      KOSPI/
      KOSDAQ/
```

`logs/`와 `storage/`는 실행 시 자동 생성됩니다.

## 데이터 처리 정책

- 결측치: 날짜와 종가가 없거나 종가가 0 이하인 row는 제거합니다.
- 거래정지: 종가가 유효하면 거래량 0 row도 유지합니다. 모델 입력에서 휴장/정지 상태를 구분할 수 있게 하기 위함입니다.
- 신규상장: 상장일 이후 존재하는 데이터만 저장합니다. 강제로 과거 날짜를 채우지 않습니다.
- 상장폐지: 기본 KOSPI/KOSDAQ 목록은 현재 상장 종목 중심입니다. 상장폐지 종목 히스토리는 추후 `FinanceDataReader`의 `KRX-DELISTING` 소스를 별도 배치로 붙이는 방식이 적합합니다.
- 수집 실패: 개별 종목 실패는 전체 배치를 중단하지 않고 `logs/failed_tickers_*.csv`에 기록합니다.

## 저장 포맷 결정

- DB 저장: 운영 파이프라인 기본값입니다. 기존 `public.price_data`에 upsert합니다.
- CSV 저장: 의존성이 적고 샘플 검증/수동 점검에 좋습니다.
- Parquet 저장: 모델 학습/대량 데이터셋 배포에 적합합니다. `pyarrow` 또는 `fastparquet`가 필요합니다.

현재 기본값은 `storage=both`, `file_format=csv`입니다. Kaggle/모델 학습용 단일 `price_data.parquet`는 기존 `AI/scripts/extract_to_parquet.py` 흐름으로 DB에서 추출하는 편이 기존 파이프라인과 가장 잘 맞습니다.
