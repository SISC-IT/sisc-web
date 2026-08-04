# Data Collector

`data_collector`는 AI/트레이딩 파이프라인이 사용하는 원천 데이터를 수집하는 작업 공간입니다. 국내 주식 일봉 OHLCV 수집은 `components/korea_stock_data.py`와 `scripts/collect_korea_stocks.py`에서 담당합니다.

## 미국 기업 뉴스 수집 (#377)

`components/news/`는 SEC 이벤트 저장 여부와 관계없이 미국 기업별 최신 뉴스를
수집합니다. 현재 universe는 2026-07-27 기준 S&P 100 snapshot이며, 복수
주식 클래스를 회사 단위로 합쳐 100개 issuer와 101개 ticker를 포함합니다.
예를 들어 `GOOG`와 `GOOGL`은 같은 Alphabet `company_key`와 CIK에 연결됩니다.

Google News RSS 수집기는 정식 회사명·승인 별칭·`{ticker} stock`을 하나의
OR 검색식으로 조회합니다. 게시 시각 원문과 UTC 시각을 함께 보존하고,
회사 관련도 규칙, URL 정규화, 정확 중복 제거, 신디케이션 후보 그룹을
적용합니다. 최상위 `collection_status`는 `success`, `partial_success`,
`failed` 생명주기로 유지하고, `collection_outcome`에서 `no_results`,
`no_relevant_news`, `provider_error`를 구분합니다. 여러 회사 중 일부가
실패하면 `partial_success`와 non-zero 종료 코드로 알립니다.

Apple 한 회사의 최근 2시간을 stdout JSON으로 확인:

```bash
python AI/modules/data_collector/scripts/collect_company_news.py \
  --tickers AAPL \
  --lookback-hours 2
```

S&P 100 전체 결과를 파일로 저장:

```bash
python AI/modules/data_collector/scripts/collect_company_news.py \
  --output AI/modules/data_collector/storage/company_news/latest.json
```

Google RSS는 역사 archive의 완전성·pagination을 보장하지 않으므로 이
실행기의 조회 범위는 최대 72시간입니다. 최근 5년 백필은 역사 조회 계약이
확인된 Infomax 같은 별도 provider가 있어야 구현할 수 있습니다. 현재 출력의
`company_relevance_score`는 규칙 기반 회사 관련도이며 SEC 공시 관련도나
확률이 아닙니다. 정답 기사 집합이 없으므로 `article_omission_rate`는
`null`로 남기고, 대신 request/invalid item 비율과 feed 포화 의심 여부를
관측 지표로 기록합니다.

수집 코어 자체는 DB나 SEC 수집기에 의존하지 않습니다. 후속 저장 계층은
`--persist`를 선택했을 때만 사용하며, SEC 이벤트 연결도 별도 one-shot
linker로 실행합니다. 원문 본문 다운로드와 AI 요약은 수행하지 않습니다.
공급자가 제공한 snippet이 있으면 사용하고, 없으면 제목만 남깁니다.

네트워크·DB 없이 RSS 파싱, 시각 변환, 관련도, 중복, 장애 격리를 검증:

```bash
python AI/tests/verify_company_news.py -v
```

### PostgreSQL 저장 및 SEC 이벤트 연결

백엔드 시작 시 Flyway의 `V5__company_news_storage.sql`이 뉴스·수집 실행·SEC
이벤트·이벤트-뉴스 연결 테이블을 생성합니다. DB 환경변수(`DB_HOST`,
`DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`)를 설정한 뒤 `--persist`를
사용하면 기사 identity와 회사 연결을 멱등 upsert합니다.

```bash
python AI/modules/data_collector/scripts/collect_company_news.py \
  --lookback-hours 2 \
  --persist
```

수집기는 PostgreSQL advisory lock을 사용하므로 이전 실행이 끝나지 않았으면
종료 코드 `5`와 `already_running` 결과를 반환합니다. DB 저장 실패는 종료
코드 `6`입니다. 서버에서는 다음 one-shot 서비스를 매시간 실행합니다.

```bash
docker compose --profile jobs run --rm ai-news
```

SEC 수집기의 `sec_filings`와 `sec_filing_documents`를 `sec_events` 계약으로
가져오고, CIK 우선으로 공시 전 24시간부터 후 48시간까지 기사를 연결합니다.
같은 명령을 반복해도 `(event_id, article_id)`가 갱신될 뿐 중복되지 않습니다.

```bash
docker compose --profile jobs run --rm ai-event-news
```

공시 후 48시간이 지나기 전인 이벤트는 한 시간 간격으로 다시 계산됩니다.
공시 원문·Exhibit 99.1·기사 제목·snippet의 규칙 기반 근거를 사용해
`DIRECT`, `CONTEXT`, `UNRELATED`를 구분합니다.

PostgreSQL 통합 테스트는 격리된 임시 schema를 생성하고 제거합니다.

```bash
NEWS_TEST_DATABASE_URL=postgresql://... \
  python AI/tests/verify_company_news_storage.py -v
```

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
    news/
      providers/
        google_news_rss.py
      config.py
      contracts.py
      dedup.py
      pipeline.py
      relevance.py
      windows.py
    news_data.py
    korea_stock_data.py
  config/
    news_collection.json
    sp100_companies.json
    korea_stocks.json
  logs/
    failed_tickers_YYYYMMDD_HHMMSS.csv
    korea_stock_data_YYYYMMDD.log
  scripts/
    collect_company_news.py
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
