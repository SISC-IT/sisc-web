# FRED/ALFRED 거시 빈티지 운영 가이드

DATA-011~013의 세로형 저장, 금리·신용·물가·고용·FX·원유 25개 시리즈, 원본 보존과 필수 as-of 조회를 구현했다. 기존 `macroeconomic_indicators`의 최신값을 과거 빈티지로 변환하지 않는다. 별도 `ingestion` 스키마에 원천 이력을 다시 수집한다.

## 데이터와 시간 정책

- `003_macro_vintages.sql`: `series_catalog`, `series_observation`, `series_coverage`, `series_run_observation`. 기존 migration 명령이 001→002→003 순서로 적용한다.
- `series_catalog.py`: 기본 25개 ID와 분류. 이름·단위·빈도·계절조정은 FRED metadata로 채운다. 수집 후 단위·빈도 변경은 격리하며 명시적인 버전 전환이 필요하다.
- `fred.py`: 원본 HTTP JSON, 재시도·속도 제한, 모든 빈티지 페이지 수집·검증, 요청 크기 초과 시 기간 분할과 원래 유효기간 복원.
- `series_store.py`: 불변 관측값, 수집 당시의 전체 행 구성과 유효기간 변경 이력, as-of 조회.
- `macro_cli.py`: 등록·수집·일일 scheduler·조회·상태 CLI. scheduler heartbeat와 source/dataset advisory lock을 사용한다.

FRED가 제공하는 `realtime_start/end`는 날짜 단위의 양 끝 포함 구간이다. 정확한 발표 시각을 알 수 없으므로 `published_at`은 NULL이고, **빈티지 날짜 다음 날 America/Chicago 자정**을 `available_at`으로 사용한다(`fred_next_day_chicago_v1`). DST를 반영한다. 정확한 장중 release timestamp를 제공하는 구현은 아니다.

예: 빈티지 날짜가 2020-04-29이면 2020-04-30T05:00:00Z부터 사용할 수 있다. 기존 빈티지가 2020-05-27까지 유효하고 다음 빈티지가 05-28이면, 기존 값은 새 값의 사용 가능 시각인 05-29T05:00:00Z에 닫힌다. 수정치를 이전 시점에 노출하지 않는다. `.`은 missing 상태로 보존하여 과거 유효값으로 임의 대체하지 않는다. 이 adapter는 관측기간 시작 이후 발표된 실현값만 지원하며 예측 시리즈를 지원하지 않는다.

관측기간은 등록한 start/end로 제한하지만 실시간 이력은 `1776-07-04`~`9999-12-31`, `output_type=1`, `units=lin`으로 요청한다. FRED의 JSON 2,000 vintage 제한에 걸리면 실시간 기간을 재귀 분할한다. 인접한 경계에서 잘린 동일값 구간만 연결하며 진짜 수정 경계와 누락 구간을 유지한다. HTTP 원본 외에 부모 raw ID 목록과 복원 결과를 담은 `fred-reconstructed-intervals-v1` manifest를 보존한다. 분할 요청도 `--max-pages` 예산에 포함된다.

수집은 등록 관측범위 전체를 다시 확인한다. 오래된 관측값의 새 수정이나 삭제를 놓치지 않기 위한 선택이며, 매일 긴 기간을 조회하는 비용이 있다. 같은 빈티지의 값 변경은 격리한다. 빈티지의 유효 종료일 변경은 별도 불변 행으로 보존하고, 성공한 전체 스캔의 행 구성을 기록하여 삭제·복원도 재현한다. 빈 응답·부분 페이지·중복/중첩·요청 예산 초과·전부 missing인 결과는 성공 처리하지 않는다. 모든 페이지가 검증된 후 관측값·스캔·run 성공·watermark를 한 트랜잭션으로 저장한다.

API 키는 `.env`의 `FRED_API_KEY`에서 읽는다. archive metadata에 API 키·요청 URL을 넣지 않고 오류 응답 본문은 제거한다. 성공 본문에 키가 포함돼도 보존하지 않고 실패한다.

## 실행

```bash
python -m pip install -r AI/requirements.ingestion.txt
python -m AI.orchestration.cli migrate
python -m AI.orchestration.cli series-register --defaults --start 2018-01-01
python -m AI.orchestration.cli fred-collect
python -m AI.orchestration.cli series-status
python -m AI.orchestration.cli series --ids GDP CPIAUCSL --start 2020-01-01 --end 2020-12-31 --as-of 2020-05-29T05:00:00Z
```

DB 선택은 전역 `--db` 옵션이다(예: `python -m AI.orchestration.cli --db TEST_DB fred-collect`). 별도 테스트 DB 환경변수는 기존 connection 모듈의 `TEST_DB_HOST/PORT/USER/PASSWORD/NAME` 형식을 따른다. 기본 명령은 프로젝트의 기본 DB 설정을 사용하므로 운영 적용 전 대상 DB를 확인한다.

특정 시리즈·고정 관측범위:

```bash
python -m AI.orchestration.cli series-register --ids GDP --category growth --start 2018-01-01 --end 2025-12-31
python -m AI.orchestration.cli fred-collect --ids GDP --page-limit 10000 --max-pages 100
python -m AI.orchestration.cli fred-schedule --interval 86400
```

재등록은 범위를 변경한다. `--end`가 없으면 수집할 때마다 현재 날짜까지 확대한다. 등록 범위를 축소해도 기존 불변 데이터는 삭제하지 않는다. `series-status`는 현재 등록 범위를 확인한 최근 스캔, 실제 응답의 첫/마지막 유효 관측일, missing 빈티지 행 수와 마지막 실행 상태를 보여 준다. 수집이 30시간 이상 오래됐거나 마지막 실행이 실패하면 상태 명령이 1로 종료한다. 이는 **수집 최신성** 점검이며 공식 발표 일정에 따른 데이터 지연 SLO가 아니다. 최신 스캔이 성공했더라도 제공자의 짧은 과거 범위나 발표 지연이 있을 수 있다.

Compose는 기존 파일과 overlay를 함께 사용한다. migration과 catalog 등록 후 `fred` profile의 `ingestion-fred` 서비스를 실행한다. 시작 절차는 [운영 README](README.md)의 overlay 절차를 따른다. Docker 이미지 실행·운영 서버 배포 검증은 아직 수행하지 않았다.

Python SDK:

```python
from AI.orchestration.series_store import SeriesStore

rows = SeriesStore().series(
    ["GDP"], start="2020-01-01", end="2020-12-31",
    as_of="2020-05-29T05:00:00Z",
)
```

`as_of`는 필수이며 timezone이 있어야 한다. `data_as_of`를 추가하면 그 시각까지의 수집 timestamp를 가진 전체 스캔만 사용한다. 생략하면 현재 알고 있는 원천 이력으로 과거 공개 시점을 조회한다. 정확한 실행 재현에는 조회 결과를 snapshot과 hash로 보존한다. 후속 구현은 [품질·스냅샷 가이드](QUALITY_SNAPSHOTS.md)를 참조한다.

## 실제 검증 결과와 한계

2026-09-23 로컬 전용 PostgreSQL에서 **기본 25개 모두 수집 성공, 빈티지 행 41,255개**를 확인했다. 2018-01-01부터 요청했으며 아래는 실제 응답 범위다. 운영 DB에 적재한 결과가 아니다.

| 분류 | 시리즈 | 실제 범위 관련 사항 |
|---|---|---|
| 단기금리 | EFFR, SOFR | SOFR는 2018-04-03부터 |
| 국채금리 | DGS3MO, DGS6MO, DGS1, DGS2, DGS5, DGS10, DGS20, DGS30 | 최초 유효 관측 2018-01-02 |
| 장단기차 | T10Y2Y, T10Y3M | 최초 유효 관측 2018-01-02 |
| 신용스프레드 | BAMLH0A0HYM2, BAMLC0A0CM | 응답은 2023-09-25부터. 2018년까지 완전한 이력 확보로 간주하지 않음 |
| 물가 | CPIAUCSL, CPILFESL, PCEPI, PCEPILFE | 최초 관측기간 2018-01-01 |
| 성장·고용 | GDP, UNRATE, PAYEMS, ICSA | GDP 등 2018-01-01, ICSA 2018-01-06부터 |
| FX·원유 | DEXJPUS, DCOILWTICO, DCOILBRENTEU | 최초 유효 관측 2018-01-02 |

GDP와 기간 분할이 필요한 DGS10 재수집 신규 삽입은 각각 0건이었다. 실제 2020년 1분기 GDP는 다음을 확인했다(단위: Billions of Dollars).

| as_of (UTC) | 결과 |
|---|---|
| 2020-04-30 04:59:59 | 아직 사용 불가 |
| 2020-04-30 05:00:00 | 21,537.940 (04-29 빈티지) |
| 2020-05-29 04:59:59 | 21,537.940 |
| 2020-05-29 05:00:00 | 21,534.907 (05-28 빈티지) |

로컬 PostgreSQL에서 SEC/가격 회귀를 포함한 **70개 테스트가 통과**했고 Ruff 검사도 통과했다. 발표 경계, DST, 수정·missing, 재수집 멱등성, 삭제/복원 및 수집시각 cutoff, 부분/빈 응답, 분할 요청 예산과 경계 복원, 값·단위 충돌, 불변 trigger를 검증한다.

DQ runner·로컬 알림·JSON/Parquet snapshot·baseline 재현은 [후속 구현](QUALITY_SNAPSHOTS.md)에 포함됐다. 남은 범위는 공식 발표 기준 SLO·외부 알림·HTTP API, 신용 시리즈의 더 긴 이력을 제공할 데이터 공급원, 50사 golden set과 실제 운영 전환이다. 별도의 Treasury/NY Fed/EIA 직접 connector는 추가하지 않았다.

공식 계약: [FRED observations](https://fred.stlouisfed.org/docs/api/fred/series_observations.html), [real-time period](https://fred.stlouisfed.org/docs/api/fred/realtime_period.html).
