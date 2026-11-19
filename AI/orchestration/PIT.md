# 후속 개편: Security history / Corporate action / SEC PIT

기존 운영 기반 위에 DATA-006~010의 수집·저장·조회·계산 경로를 구현했다. legacy 재무 테이블을 날짜로 소급 변환하지 않으며 `company_fundamentals`는 기존 용도로 유지한다. 신규 조회는 `AsOfDataset` Python SDK와 CLI를 제공한다. Spring HTTP endpoint와 웹 화면은 아직 연결하지 않았다.

## 구조와 시간 정책

- migration 002: `instrument`, `reference_record`, `sec_issuer`, `filing`, `metric_catalog`, `fundamental_fact`. migration 005는 SEC daily feed에서 복구한 날짜만 확인 가능한 filing의 시간 근거를 별도로 저장한다.
- `reference_record`는 identifier, universe membership, corporate action, 검증된 raw bar의 출처별 revision 저장소다. 유효기간은 `[valid_from, valid_to)`이며 수정 공지는 새 `available_at` 행으로 추가한다.
- `filing`과 `fundamental_fact`는 발행사 CIK에 귀속된다. 복수 share class의 재무를 ticker 단위로 복제하거나 회사 재무의 shares를 해당 주식종류의 shares로 간주하지 않는다.
- `SEC_USER_AGENT`는 조직명과 담당 이메일을 포함해야 한다. 로컬 `.env`와 배포 환경에서 주입하며 소스 코드·로그·요청 metadata에 값을 기록하지 않는다.
- 일반 SEC JSON은 `published_at=accepted_at`, `available_at=accepted_at+60초`다. SEC 공식 daily feed 원문으로 filing 날짜만 복구한 경우 `accepted_at/published_at=NULL`, `availability_basis=sec_feed_day_end`로 저장하고 미국 동부시간의 해당 날짜 종료 후 60초부터 노출한다. 정확한 접수 시각으로 표현하지 않는다.
- 연결되지 않은 fact와 모순된 natural key는 격리한다. `filed_date`나 분기 말로 정확한 공개 시각을 추정하지 않는다.
- 읽기는 timezone-aware `as_of`를 강제한다. cutoff 필터 뒤에 revision 순위를 계산하므로 미래 수정치가 과거 원본을 가리지 않는다. DB 조회 시간대는 UTC로 고정한다.
- filing/fact/reference의 UPDATE/DELETE는 DB trigger로 금지한다. fact의 CIK·공개 시각·가용 시각은 연결된 filing과 DB trigger로 대조한다.

## migration과 SEC 수집

```bash
python -m AI.orchestration.cli migrate
python -m AI.orchestration.cli sec-register --cik 320193
python -m AI.orchestration.cli sec-register --cik 789019
python -m AI.orchestration.cli sec-collect
# 한 번만 명시한 issuer를 수집할 수도 있음
python -m AI.orchestration.cli sec-collect --ciks 320193 789019
```

현재 schema version은 5이며 재실행해도 기존 데이터를 삭제하지 않는다. migration은 한 transaction과 advisory lock에서 실행한다. 운영 DB migration은 아직 수행하지 않았다.

SEC submissions 최근 목록뿐 아니라 `filings.files`가 가리키는 과거 목록도 읽는다. 모든 HTTP payload는 JSON 파싱 전에 content-addressed archive에 저장한다. non-200 응답도 보존한다. metadata는 경로·status·ETag·Last-Modified만 허용한다. 정확한 원문 filing HTML/iXBRL까지 다운로드하는 connector는 후속이며, 이번 Bronze는 SEC submissions/companyfacts **원본 JSON**이다.

SEC 전용 DB source lock 아래 요청 간격을 0.2초 이상으로 제한한다. 429/5xx와 일시적 연결 오류만 최대 3회 재시도한다. Retry-After와 backoff/jitter를 적용하고 120초보다 긴 Retry-After는 다음 실행으로 넘긴다. URL redirect와 SEC 밖 주소 요청은 허용하지 않는다. 다른 애플리케이션의 SEC 트래픽까지 이 lock이 제어하지는 않는다. ETag는 기록하지만 conditional GET은 아직 사용하지 않는다.

성공 시 filing/fact, run 상태, issuer watermark를 원자적으로 commit한다. SEC watermark는 일봉 거래일 연속성이 아니라 전체 companyfacts 조회에서 검증한 마지막 공시 접수 시각이다. 다음 수집도 전체 issuer JSON을 재조회하여 과거 비교기간 수정 공시를 놓치지 않는다. 자연키 재수집은 중복 없이 처리한다.

```bash
python -m AI.orchestration.cli sec-schedule --interval 86400
# Docker opt-in SEC profile (migration과 issuer 등록 후)
docker compose -f docker-compose.yml -f docker-compose.ingestion.yml --profile sec up -d ingestion-sec
python -m AI.orchestration.cli health --scheduler-id sec-companyfacts-v2
```

SEC scheduler는 일간 companyfacts용이다. 5~15분 filing trigger 및 신규 공시 이벤트 DAG는 후속 범위다. 각 run 실패/격리 결과는 기존 status/quarantine에서 확인한다.

## 재무 조회와 파생 계산

```bash
python -m AI.orchestration.cli fundamentals --cik 320193 --metrics revenue operating_cash_flow --as-of 2018-09-22T00:00:00Z
python -m AI.orchestration.cli features --cik 320193 --as-of 2018-09-22T00:00:00Z --industry-model industrial
```

```python
from AI.orchestration.pit_store import PitStore, AsOfDataset
from AI.orchestration.features import financial_features, valuation_features

api = AsOfDataset(PitStore("db"))
facts = api.fundamentals("320193", as_of="2018-09-22T00:00:00Z")
features = financial_features(
    facts, as_of="2018-09-22T00:00:00Z", industry_model="industrial"
)
```

US-GAAP USD 기반 40개 이상 metric을 정의했다. 원본 tag·우선순위·단위·기간·accession·mapping version·run/raw ID를 보존한다. 공시의 fy/fp는 비교기간의 실제 fiscal quarter를 뜻한다고 가정하지 않는다. 따라서 분기는 실제 기간 경계로 판정한다. IFRS/회사 custom tag/segment 확장은 아직 없으며, 은행·보험·REIT에 산업재 비율을 자동 적용하지 않는다.

누적 현금흐름은 같은 시작일의 누적값을 차감하여 분기로 변환한다. 직접 보고된 standalone quarter와 수정 공시를 비교하여 가용 시점과 기간을 유지한다. TTM v2는 최신 기간의 공개된 350~380일 연간 총액을 우선 사용하며, 그렇지 않으면 연속된 4개 분기·동일 단위·350~380일 기간을 요구한다. 이후 부분 수정 공시가 있으면 이전 연간 총액을 재사용하지 않는다. EPS와 가중평균주식수는 가산형 흐름이 아니므로 YTD 차분을 금지한다. [v2 변경 및 실측 결과](FEATURES_V2.md)를 참조한다.

계산 결과는 `value`, `status`, `calculation_version`, `available_at`, `source_fact_ids`를 포함한다. FCF, EBIT, 표준 계산 EBITDA, lease 제외 total/net debt, 평균 invested capital 기준 ROIC를 제공한다. 입력 가용 시각의 최댓값보다 먼저 feature를 노출하지 않는다. 0/음수 분모는 NULL과 상태로 반환한다.

`valuation_features`는 별도로 제공받은 PIT market cap·debt·preferred·minority·cash·TTM EBITDA로 EV 및 EV/EBITDA를 계산한다. 각 입력에 `value`, `unit=USD`, `available_at`, `period_end`, `source_fact_ids`가 필요하다. 미공시 preferred/minority를 0으로 채우지 않는다. 현재 주식수와 과거 가격을 곱해 market cap을 자동 생성하지 않는다.

감가상각+상각과 감모상각(depletion)을 포함한 합산 tag는 서로 다른 metric이다. 적합한 D&A가 없으면 EBITDA를 채우지 않는다. 부채 구성이나 과세 전 이익 등의 최신 기간이 맞지 않아도 `missing_inputs`/`unaligned_inputs`를 반환한다. 파생 feature의 DB materialization은 후속 범위다. 재무 입력·reference·bars snapshot export는 [품질·스냅샷 가이드](QUALITY_SNAPSHOTS.md)를 참조한다.

## 종목 이력과 기업행동 import

현재 ticker 목록을 historical universe로 자동 복제하지 않는다. 거래소 공지나 권한 있는 데이터 제공자의 확인된 이력을 JSON으로 준비한다. 아래는 형식 설명을 위한 **가상 종목 예시**다.

```json
{
  "instruments": [{
    "instrument_id": "11111111-1111-4111-8111-111111111111",
    "name": "Synthetic example", "asset_type": "equity", "currency": "USD",
    "exchange": "XNYS", "timezone": "America/New_York"
  }],
  "records": [{
    "instrument_id": "11111111-1111-4111-8111-111111111111",
    "kind": "identifier", "source": "synthetic_example", "source_key": "symbol-1",
    "published_at": "2025-01-01T00:00:00Z", "available_at": "2025-01-01T00:00:00Z",
    "valid_from": "2025-01-02", "valid_to": null,
    "payload": {"identifier_type": "ticker", "value": "EXAMPLE", "venue": "XNYS"}
  }]
}
```

| kind | payload 필수 필드 |
|---|---|
| identifier | `identifier_type=ticker/cik/isin`, `value`; ticker는 `venue`, CIK는 10자리 |
| membership | `universe_id`; effective 편입/편출은 valid_from/to |
| corporate_action / split | `action_type=split`, `ratio_numerator`, `ratio_denominator`; effective date는 valid_from |
| corporate_action / cash_dividend | `action_type=cash_dividend`, `cash_amount`, `currency`, `share_basis=post_split`; ex-date는 valid_from |
| bar | `open/high/low/close/volume`, `price_basis=raw`, `currency`, timezone-aware `session_close`; session date는 valid_from |

```bash
python -m AI.orchestration.cli reference-import verified-history.json
python -m AI.orchestration.cli resolve --type ticker --value EXAMPLE --venue XNYS --on 2025-02-01 --as-of 2025-02-01T12:00:00Z
python -m AI.orchestration.cli universe --universe-id sp500 --on 2025-02-01 --as-of 2025-02-01T12:00:00Z
python -m AI.orchestration.cli bars --instrument-id 11111111-1111-4111-8111-111111111111 --start 2025-01-02 --end 2025-02-01 --basis raw --as-of 2025-02-02T00:00:00Z
```

새로운 공지의 revision은 같은 source/source_key와 늦은 available_at으로 넣는다. 과거 데이터는 삭제하지 않는다. 같은 ticker가 다른 종목에 재사용돼도 날짜별 identifier는 안정된 UUID로 해결한다. 중첩된 identity가 여러 UUID를 가리키면 임의로 하나를 고르지 않고 오류를 반환한다. CIK identifier를 등록하면 `instrument_fundamentals(instrument_id, on=..., as_of=...)`로 연결할 수 있다.

`bars`는 검증된 reference raw bar만 대상으로 하며 Yahoo SDK ledger를 raw로 재해석하지 않는다. basis는 필수다. `split_adjusted`는 조회 구간 끝 기준 split만 반영하여 OHLC와 volume을 역방향으로 조정한다. `total_return`은 첫 관측 100에서 시작하는 index이며 **ex-date 종가 재투자**, 배당 cash는 post-split 주당 금액 정책이다. ex-date bar가 없거나 여러 action source가 충돌하면 오류를 반환한다. pay-date 현금 계정, 세금, 수수료, spin-off·합병 보상·delisting return은 아직 구현하지 않았다. raw OHLC 자체는 수정하지 않는다.

## 검증 결과와 남은 범위

2026-09-23: 로컬 전용 PostgreSQL 16에서 52개 테스트 통과. 공시 전 차단, 수정 공시 선택, future 입력 거절, YTD→quarter→TTM, ROIC/EV·분모 규칙, ticker 변경·재사용, universe 편출, split/현금배당, immutable trigger, migration 재실행과 legacy 회귀를 포함한다.

실제 SEC JSON을 별도 테스트 DB에 적재했다: Apple 6,258 facts, Microsoft 6,606 facts. Apple 재수집은 신규 삽입 0건이었고, 2018/2026 as-of 조회 및 FCF 계산을 확인했다. 접수시각·filing 연결·schema 거절은 없었다. 2개 기업 smoke test이며 50개 기업 8년 golden record를 검증했다는 뜻은 아니다. 테스트 DB와 archive는 로컬 `.venv-ingestion` 아래에 있고 운영 DB에는 쓰지 않았다.

후속: 거래소 과거 universe/상장폐지·기업행동 실데이터 확보, 회사별 taxonomy 검증 및 50사 golden set, 공식 발표 기준 SLO·외부 알림·HTTP API. SEC scheduler의 배포 및 실제 서버 재부팅 검증도 남아 있다. 후속 구현은 [거시 데이터 가이드](MACRO.md)와 [품질·스냅샷 가이드](QUALITY_SNAPSHOTS.md)를 참조한다.

공식 규약: [SEC API](https://www.sec.gov/search-filings/edgar-application-programming-interfaces), [SEC Developer Resources](https://www.sec.gov/about/developer-resources).

2026-10-01 후속으로 50개 발행인 검증 도구와 기간 제한 수집을 추가했다. 자동 원본 대조·회계연도 충돌·계산 가능 범위는 [SEC 표본 검증](SEC_VALIDATION.md)과 [실행 결과](SEC_VALIDATION_RESULTS.md)를 참조한다. 전체 표본의 독립 회계 검토 완료를 뜻하지 않는다.
