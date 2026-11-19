# 품질 점검과 불변 스냅샷

DATA-014~015의 DQ runner, 로컬 알림 이력, JSON/Parquet snapshot exporter와 오프라인 baseline 재현을 추가했다. migration은 기존 `migrate` 명령으로 004까지 적용한다. 운영 서버 배포·외부 알림 채널 연결·HTTP API는 아직 수행하지 않았다.

## 품질 점검

```bash
python -m pip install -r AI/requirements.ingestion.txt
python -m AI.orchestration.cli migrate
python -m AI.orchestration.cli dq-run
python -m AI.orchestration.cli dq-alerts --after-id 0 --limit 100
python -m AI.orchestration.cli dq-schedule --interval 900
```

전역 `--db TEST_DB`로 전용 DB를 선택할 수 있다. 기본 명령은 프로젝트 기본 DB 설정을 사용한다. `dq-run`은 결과를 DB에 저장하며, 실패 항목이 있으면 종료 코드 1이다. 외부 서비스에 메시지를 보내지 않는다. `dq-schedule`은 발생·해소된 알림만 stdout에 출력하고 매 검사 결과는 DB에 보존한다. `dq-alerts`는 순번 기반으로 이력을 읽는다. 같은 장애가 계속되면 알림을 반복 생성하지 않는다.

| 검사 | 판정 |
|---|---|
| 등록 또는 관측된 dataset 존재 | 빈 플랫폼을 정상으로 보고하지 않음 |
| 수집 최신성 | 마지막 **0행이 아닌 성공** 이후 허용 시간 초과 또는 성공 이력 없음 |
| 마지막 완료 실행 | failed, quarantined, interrupted, unverified를 실패로 표시 |
| 0행 성공 | fetched_count=0인 success 탐지. 멱등 재수집의 inserted_count=0은 정상 |
| 작업 heartbeat | running 상태에서 heartbeat가 기본 10분 이상 중단됨 |
| 7일 성공률 | 완료 실행 중 검증된 성공 비율 99% 미만은 warning. skipped/running 제외 |
| 일봉 완전성 | 등록된 실제 거래 세션 대비 ledger와 기존 호환 테이블 모두에 있는 날짜가 99.5% 미만 |
| FRED 등록범위 | 등록 start/end를 포함하는 전체 스캔이 없는 경우 실패 |
| 시간 순서 | 재무·공시·reference·macro의 공개/사용 가능 시각 역전 검사 |

검사 대상은 등록된 가격 파티션·SEC issuer·FRED series와 실행 이력에 등장한 dataset/partition이다. 수집 이력조차 없는 미등록 legacy 작업은 발견할 수 없다. 과거 실행이 있는 dataset은 계속 감시 대상이므로 운영 전 대상·주기에 맞는 정책을 설정한다. 수동 reference import에도 기본 정책이 적용된다.

기본 수집 최신성은 30시간, 미국 일봉은 주말을 고려해 96시간이다. 시장 데이터의 더 짧은 누락은 거래 캘린더 완전성 검사가 별도로 탐지한다. 정책은 dataset별로 변경하고 검사 당시 설정을 `dq_run.policies`에 보존한다.

```bash
python -m AI.orchestration.cli dq-policy --dataset sec_companyfacts --freshness-hours 2 --heartbeat-minutes 10 --success-ratio 0.99 --completeness-ratio 0.995
```

FRED 정책을 변경하면 series catalog의 freshness_hours에도 반영한다. 설정을 바꿔도 scheduler 주기 자체는 바뀌지 않는다. 짧은 SLO에는 실제 수집 주기도 맞춰야 한다.

`dq_run`, `dq_result`, `dq_alert`는 불변 이력이며 `dq_incident`는 현재 활성 상태다. 체크는 일관된 repeatable-read DB 뷰에서 수행하고, 결과·발생/해소 알림은 한 트랜잭션으로 저장한다. 품질 scheduler는 `quality` compose profile로 opt-in 실행한다. heartbeat healthcheck는 scheduler 생존만 의미하며 데이터 품질은 `dq-run` 결과를 확인한다.

현재 검사는 공식 SEC release 후 15분 수집 비율, 공식 거시 발표 캘린더 기반 지연, feature 분포·원천 간 충돌·재무 파생 지연까지 측정하지 않는다. DB 자연키 제약과 기존 ingestion quarantine을 대체하지도 않는다. 특히 FRED 전체 스캔 성공은 요청한 모든 과거 날짜가 제공됐다는 뜻이 아니다.

## 스냅샷 작성

스펙 JSON은 수집시각 cutoff인 `data_as_of`, 이름이 고유한 `queries`, 선택적인 `labels`로 구성한다. 모든 query의 `as_of`는 필수이며 `data_as_of` 이후일 수 없다. `data_as_of`도 미래 시각일 수 없다.

```json
{
  "data_as_of": "2026-09-23T00:00:00Z",
  "labels": {"feature_set": "macro_release_comparison_v1"},
  "queries": [
    {
      "name": "gdp_initial",
      "kind": "series",
      "args": {
        "ids": ["GDP"],
        "start": "2020-01-01",
        "end": "2020-01-01",
        "as_of": "2020-05-01T00:00:00Z"
      }
    }
  ]
}
```

이 예의 `data_as_of`는 실행 환경에서 실제 데이터가 수집된 이후 시각으로 바꿔야 한다. 허용 kind는 `series`, `fundamentals`, `references`, `universe`, `bars`이며 args는 기존 SDK와 같다. bars에는 instrument_id와 basis를 명시한다. 시점별 전략 입력은 각 decision 시점의 `as_of`로 별도 query를 작성한다. 마지막 시점에 알려진 재무/거시 값을 전 기간의 신호로 사용하는 방식은 지원하지 않는다.

```bash
python -m AI.orchestration.cli snapshot-export --spec snapshot-spec.json --output AI/data/snapshots --id macro_release_v1
python -m AI.orchestration.cli snapshot-verify AI/data/snapshots/macro_release_v1 --expected-hash <content_hash>
```

`allow_empty: true`를 query에 명시하지 않으면 0행 export는 실패한다. 모든 조회는 하나의 repeatable-read 트랜잭션에서 수행하므로 수집 중간의 서로 다른 DB 상태를 섞지 않는다. SEC/reference SDK에도 `AsOfDataset(store, data_as_of=...)`를 추가했다. 기존 호출은 현재 알려진 이력을 사용한다.

생성물:

- `data.json`: 이름별 조회 결과. Decimal은 손실 없는 십진 문자열, 날짜·UUID도 문자열로 보존한다.
- `data.parquet`: `query_name`, `row_index`, `payload_json` 열. 서로 다른 dataset 스키마를 담는 버전 1 envelope 형식이며 숫자 분석용 wide typed schema는 아니다. JSON payload를 각 dataset 계약에 따라 파싱한다.
- `manifest.json`: 마지막에 쓰는 발행 표시. query spec, schema migration 버전, code SHA와 현재 Python/SQL 소스 트리 hash, 원천 raw/run/checksum lineage, row count, 파일별 SHA-256, 논리 content hash를 포함한다. Git 미커밋 변경도 소스 트리 hash로 구별한다.
- DB `dataset_snapshot`: snapshot ID·content hash·manifest hash·디렉터리의 불변 등록부.

같은 ID 디렉터리를 덮어쓰지 않는다. 실패한 쓰기의 디렉터리는 자동 삭제하지 않으며 manifest가 없거나 파일 hash가 맞지 않으면 유효한 snapshot으로 읽지 않는다. 파일 발행 후 DB 등록만 실패했다면 복구할 수 있다.

```bash
python -m AI.orchestration.cli snapshot-register AI/data/snapshots/macro_release_v1
```

등록부와 파일 시스템은 단일 트랜잭션이 아니므로 위 복구 절차가 필요하다. 다른 내용·위치에 이미 등록된 ID는 거절한다. 이는 exporter의 덮어쓰기 방지와 hash 검증이며 저장 장치의 WORM/Object Lock을 설정하는 구현은 아니다. 신뢰하는 manifest/content hash는 실행 기록에도 보존한다.

`data_as_of`는 저장된 ingestion timestamp에 대한 cutoff다. 당시 아직 commit되지 않았던 트랜잭션의 가시성까지 과거 DB 상태로 되돌리는 기능은 아니다. **정확한 재현에는 저장된 snapshot과 hash를 사용**하며 현재 DB에서 스펙을 다시 조회하는 것으로 대체하지 않는다.

## 오프라인 baseline

`bars` query를 `basis=total_return`으로 저장하면 DB 없이 단일 자산 매수 후 보유 baseline을 계산한다. split·배당은 기존 SDK의 ex-date 종가 재투자 규칙을 따른다. 비용·세금·리밸런싱·다중 자산 전략은 범위 밖이다.

```bash
python -m AI.orchestration.cli snapshot-baseline AI/data/snapshots/price_v1 --query prices --expected-hash <content_hash>
```

검증과 baseline 명령은 DB 연결을 만들지 않는다. 모든 파일의 hash와 JSON/Parquet 내용 동등성을 확인한 뒤 결과와 result_hash를 출력한다. 미래 수정치가 추가되어도 저장본 결과는 바뀌지 않는다.

## 검증 기록

2026-09-23 로컬 PostgreSQL에서 **79개 테스트 통과**, Ruff 통과. 새 검증은 0행 성공·발생/해소·알림 중복 방지·멈춘 작업·캘린더 누락, 동시 수집 중 repeatable-read, 데이터 cutoff 이후 수집된 수정치 차단, JSON/Parquet 변조 탐지, ID 덮어쓰기 거부, 등록 실패 복구·ID 충돌, source lineage, baseline 재현을 포함한다.

실제 FRED 테스트 DB에서 152개 DQ 검사를 실행했다. 이전에 발생했던 API 요청 실패 때문에 11개 시리즈의 7일 성공률 warning이 남았고, 현재 수집 최신성·등록범위 검사는 통과했다. 실패 이력을 지워 정상으로 만들지 않았다. 실제 GDP 최초값과 수정값을 각각 한 행씩 snapshot으로 내보냈다.

별도 합성 데이터에서는 100→50의 2:1 split와 주당 1 배당을 반영한 수익률 2%를 확인했다. 이후 가격 수정으로 현재 데이터 결과가 12%로 바뀌어도 기존 snapshot은 2%와 동일 result hash를 반환했다. 전용 PostgreSQL을 중지한 뒤 CLI의 snapshot-verify와 snapshot-baseline도 성공했다. 이는 합성 baseline 검증이며 실제 투자 전략 성과 검증이 아니다.

남은 개편 범위: 50사·8년 golden set, 거래소 과거 universe·상장폐지·기업행동 실자료 검증, 공식 발표 기준 SLO 확대, 외부 알림 연결, HTTP API, 운영 배포와 재부팅 복구 검증.
