# 데이터 플랫폼 V2: 운영 기반, SEC PIT, FRED 빈티지

`SISC-Web 백테스트 데이터 플랫폼 V2 개편안`의 운영 기반(DATA-001~005)에 이어, 종목 이력·기업행동·SEC PIT의 수집/조회/계산 경로(DATA-006~010)를 추가했다. 사용법과 검증 결과는 [SEC PIT 가이드](PIT.md)에 정리했다. FRED 빈티지와 25개 거시 시리즈 수집·as-of 조회(DATA-011~013)는 [거시 데이터 가이드](MACRO.md)를 참조한다. 전체 12주 개편 완료를 의미하지 않는다.

## 실제 저장소와 문서의 차이

| 항목 | 확인한 코드 | 이번 변경 |
|---|---|---|
| 디렉터리 | `ai/`가 아니라 `AI/` | 기존 대소문자 유지 |
| 수집 실패 격리 | crawler에 단계별 try/except가 이미 존재 | lazy import 기반 독립 Job registry, 실패 종료 코드 |
| 성공 판정 | 내부 오류를 삼키는 수집기가 있어 정상 반환도 불확실 | legacy는 `unverified`, V2는 검증·commit 후에만 `success` |
| 가격 복구 | MAX(date)+1 또는 2015/2018년부터 전량 재수집 | 전체 등록 범위의 실제 날짜 비교, 최대 20세션 단위 gap 복구 |
| 실행 주체 | compose의 AI 서비스 주석, 현재 파일에 ai-news/ai-sec 서비스 없음 | 별도 opt-in scheduler overlay |
| 스키마 | `schema.sql`은 참고용이며 운영 자동 migration이 아님 | 독립적·명시적·transactional migration 명령 |
| PIT | legacy 재무 date 기반 PER/PBR 병합 | 이번 호환 적재에서는 PER/PBR를 새로 계산하지 않음 |

## 파일과 migration

- `AI/migrations/001_dataset_operations.sql`: `ingestion` schema와 run, watermark, partition, raw, quarantine, bar ledger, heartbeat.
- `core.py`: 거래일 캘린더, 구간 계획, 연속 완료 경계, OHLCV 검증.
- `store.py`: PostgreSQL lock, 원자적 저장, 구버전 가격 테이블 동시 적재.
- `market.py`, `archive.py`, `runner.py`: Yahoo SDK 입력 보존, 정규화, 제한적 재시도, 검증·격리·저장.
- `legacy.py`: 기존 8개 수집 단계 독립 실행. 기존 crawler 옵션 유지.
- `cli.py`: 운영 점검, 파티션 등록, gaps/backfill/run/schedule/status/health.
- `docker-compose.ingestion.yml`: 재부팅 시 재시작, 영속 Bronze volume, heartbeat healthcheck.
- `AI/tests/ingestion/`: 순수 로직/adapter/runner 및 실제 PostgreSQL 통합 테스트.

기존 public 테이블 삭제·rename, 날짜를 이용한 과거 `available_at` 생성, 현재 ticker 목록을 과거 universe로 등록하는 작업은 없다. V2 미국·암호화폐 일봉은 ledger와 기존 `price_data`/`crypto_price_data`를 **같은 트랜잭션**에서 적재한다. public 테이블은 기존 최신치 호환 API이며 PIT API가 아니다.

## 실행

저장소 루트, Python 3.12에서 실행한다. 기존 학습 환경과 분리된 가상환경을 권장한다.

```bash
python -m pip install -r AI/requirements.ingestion.txt
python -m AI.orchestration.cli baseline
python -m AI.orchestration.cli migrate
```

DB 연결은 기존 `DB_HOST`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, 선택 `DB_PORT`를 사용한다. `--db TEST_DB`를 **subcommand 앞**에 지정하면 `TEST_DB_*` 환경변수를 사용한다. 로그에는 DSN, API key, 외부 예외 메시지를 출력하지 않는다.

종목별 거래소와 상장 유효기간을 검증하여 등록한다. 시작일 이전은 완전성 보장 대상이 아니다. 상장폐지 종목은 삭제하지 않고 `--end`로 범위를 제한한다. ticker 변경·재사용을 해결하는 instrument history는 후속 단계이며 이 등록부를 historical universe로 사용하면 안 된다.

```bash
python -m AI.orchestration.cli register --dataset price_us_daily --partition AAPL --calendar XNYS --start 2026-07-31
python -m AI.orchestration.cli register --dataset crypto_daily --partition BTC-USD --calendar 24/7 --start 2026-07-31
python -m AI.orchestration.cli gaps --start 2026-07-31
python -m AI.orchestration.cli backfill --dataset price_us_daily --partition AAPL --start 2026-07-31 --end 2026-08-07
python -m AI.orchestration.cli backfill
python -m AI.orchestration.cli run
python -m AI.orchestration.cli status
```

`gaps`는 V2 검증 ledger의 누락과 legacy 테이블의 누락을 각각 출력한다. legacy 데이터가 있더라도 V2 lineage가 없으면 다시 수집한다. `backfill`은 gap만, `run`은 gap과 최근 5세션을 갱신한다. 날짜 범위는 양 끝을 포함한다. 등록 범위를 확장/축소하면 기존 watermark는 무효화되고 다음 commit에서 다시 계산된다.

`gaps`는 누락 시 exit 1, `backfill/run`은 실패·격리·다른 작업의 lock 점유 시 exit 1이다. 정상적인 휴일/최신 상태에서는 fetch를 하지 않는 `noop`이다. fetch가 계획된 구간의 0행은 quarantine이다. no-op에서는 watermark를 인위적으로 이동하지 않는다.

기존 crawler도 선택적으로 실행 이력을 남길 수 있다:

```bash
python AI/modules/data_collector/crawler_bot.py --track-runs --skip-price --skip-crypto
```

내부 오류 전파·행수 계약이 없는 legacy Job은 `unverified`로 기록하고 watermark를 갱신하지 않는다. legacy 호출의 정상 종료 코드는 기존 자동화 호환을 위해 유지하지만, 모니터링은 종료 코드만으로 데이터 성공을 판단하면 안 된다. DB metadata 저장 실패도 수집 실패로 처리한다.

## scheduler 배포와 복구

기존 cron/systemd의 가격·암호화폐 실행을 확인하고 중복 실행을 제거한다. 기존 writer는 V2 advisory lock을 사용하지 않는다. 다른 legacy Job은 유지할 수 있다.

```bash
# 서버에서 코드 SHA를 빌드 인자로 고정
export CODE_SHA=$(git rev-parse HEAD)
docker compose -f docker-compose.yml -f docker-compose.ingestion.yml build ingestion
docker compose -f docker-compose.yml -f docker-compose.ingestion.yml run --rm ingestion python -m AI.orchestration.cli migrate
# 위 register 명령도 같은 run --rm 컨테이너에서 실행
docker compose -f docker-compose.yml -f docker-compose.ingestion.yml up -d ingestion
```

컨테이너는 즉시 catch-up 후 6시간마다 실행한다. Bronze는 named volume에 저장되므로 `down -v`로 삭제하지 않는다. 캘린더는 `exchange-calendars==4.11.2`로 고정하며 휴장·DST·조기폐장을 반영한다. 주식은 장 종료+90분, 암호화폐는 다음 UTC 자정+90분까지 완료를 기다린다. SDK 요청 timeout은 30초, 알려진 일시적 오류는 최대 3회 재시도한다. Yahoo SDK가 HTTP 응답을 직접 노출하지 않으므로 Retry-After/ETag/원본 HTTP 기록은 이 adapter에서 보장하지 않는다.

partition 단위 PostgreSQL session advisory lock이 중복 실행을 막는다. 작업 중 강제 종료되면 다음 lock 획득자가 남은 `running`을 `interrupted`로 전환하고 실제 누락 날짜를 다시 계산한다. 일부 뒤쪽 날짜만 성공해도 앞쪽 gap을 건너뛰어 watermark를 진행시키지 않는다. 데이터, 호환 테이블, 성공 상태, watermark는 원자적으로 commit한다. heartbeat는 별도 DB 연결로 30초마다 갱신한다.

healthcheck는 scheduler 생존 확인이다. 데이터 품질/완전성은 `gaps`, `status`, quarantine과 run 상태로 별도로 확인한다. 외부 알림 채널 연결·웹 대시보드는 후속 작업이다.

### 운영 서버 점검표

로컬에서는 설정된 DB 연결이 `OperationalError`로 실패했고 Docker/psql 실행 파일도 없었다. 실제 서버의 실행 주체·7/31 이후 데이터 현황을 확인했다고 주장하지 않는다.

서버에서 아래 결과를 내부 incident report에 기록한다. 환경변수 값이나 비밀을 공유 로그에 남기지 않는다.

```bash
git rev-parse HEAD
git status --short
docker compose ps
crontab -l
systemctl list-timers --all
python -m AI.orchestration.cli baseline
python -m AI.orchestration.cli gaps --start 2026-07-31
python -m AI.orchestration.cli status
```

각 dataset에 owner/backup owner, 실행 명령·주기, 마지막 성공, DB 최신일, 로그 위치를 기록한다. 1주 구간 복구 → 전체 gap 복구 → heartbeat 확인 → 계획된 서버 재부팅 → 동일 gap 재점검 순으로 검증한다. 실제 서버 재부팅 테스트는 아직 수행하지 않았다.

## 검증과 rollback

```bash
python -m pip install pytest ruff pglast
python -m pytest AI/tests/ingestion -q
ruff check --isolated --select E4,E7,E9,F,I AI/orchestration AI/tests/ingestion AI/modules/data_collector/crawler_bot.py
```

PostgreSQL 테스트는 `INGESTION_TEST_DSN`을 명시한 전용 DB에서만 실행한다. DB 이름이 `ingestion_test`로 시작해야 하며, 테스트는 그 DB의 ingestion 스키마와 fixture 가격 테이블을 초기화한다. 프로젝트 `.env`를 테스트 접속에 사용하지 않는다. CI는 PostgreSQL 16 서비스에서 통합 테스트를 실행한다.

2026-09-22 로컬 검증: 별도 PostgreSQL 16.14 DB에서 **30개 테스트 통과**, Ruff 검사 통과. Yahoo AAPL의 2025-09-02~03 실제 응답 2행을 보존·파싱·DQ 검증했다. 운영 DB migration, 운영 백필, Docker 이미지 실행, 실제 서버 재부팅은 수행하지 않았다.

운영 rollback은 ingestion scheduler를 중지하고 기존 실행 명령으로 되돌리는 방식이다. additive 스키마와 Bronze는 감사·재처리를 위해 보존한다. migration을 되돌리기 위해 운영 데이터를 자동 삭제하지 않는다. 이미 갱신된 public 가격은 코드 rollback으로 되돌아가지 않으므로 전환 전 백업을 보존하고 필요하면 검증된 백업/새 remediation run으로 복구한다. 후속 schema 변경은 번호가 증가하는 forward migration으로 작성한다.

## 남은 개편 범위와 수용 기준

| 범위 | 상태/후속 작업 |
|---|---|
| DATA-001 | 점검 CLI/절차 구현; 서버 실행 주체·로그 확인은 접근 가능한 서버에서 필요 |
| DATA-002 | 테이블별 row/latest baseline + 등록된 미국/crypto 일봉 gap report; 모든 dataset별 calendar 확대 필요 |
| DATA-003 | run/watermark migration, 신규 daily Job lineage 구현 |
| DATA-004 | 기존 8개 Job registry 분리; 나머지 adapter의 검증 가능한 commit 계약 필요 |
| DATA-005 | 미국/crypto 누락 구간별 멱등 backfill·재시작 catch-up 구현 |
| DATA-006~007 | 이력 import/조회와 split/배당 basis 구현; 실거래소 과거 이력 수집·표본 대조 필요 |
| DATA-008~010 | SEC HTTP/daily feed 원본, 공시 연결, 40개 이상 mapping, as-of SDK·재무 계산과 50사 원본 대조 구현; 기대 연차 범위 50/50, 원문상 미공시·회계정의 한계는 SEC_REMEDIATION_RESULTS.md 참조 |
| DATA-011~013 | 세로형 series·FRED vintage·as-of SDK와 25개 실제 수집 검증; 신용 시리즈 공급 범위 제한은 MACRO.md 참조 |
| DATA-014~015 | DQ runner·로컬 알림 이력, JSON/Parquet snapshot·오프라인 baseline 구현; 공식 발표 SLO·외부 알림·HTTP API·운영 전환은 후속 |

Yahoo adapter의 `available_at`은 실제 수신 시각이며 SDK 출력은 원본 HTTP 응답이 아니다. metadata에 `yahoo_sdk`, `dataframe-split-json-v1`로 표시한다. vendor의 OHLC는 과거 조정이 포함될 수 있으므로 이를 검증된 raw price basis로 제공하지 않는다. SEC는 원본 HTTP JSON과 접수 시각+60초 정책을 별도로 사용한다. 품질 점검·snapshot 사용법과 검증 결과는 [품질·스냅샷 가이드](QUALITY_SNAPSHOTS.md)를 참조한다. 50사 8년 golden set 완료 기준은 아직 충족하지 않았다.

참고한 adapter 계약: [yfinance 공식 구현](https://github.com/ranaroussi/yfinance/blob/main/yfinance/multi.py), [exchange-calendars 4.11.2](https://pypi.org/project/exchange_calendars/4.11.2/).
