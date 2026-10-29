# SEC 50사 검증 운영

`sec50_cohort.json`은 2018~2025 회계연도 검증을 위한 50개 발행인 표본이다. 현재 ticker 목록으로 과거 투자 universe를 구성하지 않는다. 공식 SEC ticker catalog의 원본 SHA-256과 각 CIK 출처를 보존하며, 과거 Exxon Mobil·Activision Blizzard·Seagen CIK를 별도 표기했다. JPM/BAC에는 산업기업용 EBITDA/ROIC 모델을 적용하지 않는다.

## 수집·재개

```bash
python -m AI.orchestration.cli migrate
python -m AI.orchestration.cli sec-cohort-collect --archive AI/data/bronze
python -m AI.orchestration.cli sec-cohort-collect --archive AI/data/bronze --only-uncollected
```

`--db TEST_DB`는 명령 앞의 전역 옵션이다. 운영 적용 전 전용 DB에서 먼저 실행한다. `SEC_USER_AGENT`는 기존 `.env` 설정을 사용하며 로그에 출력하지 않는다. 전체 JSON 원본 보존, 최대 초당 5회 요청, retry, source lock과 격리 규칙은 기존 SEC connector를 사용한다.

회계연도 경계와 이전 기간 계산을 위해 수집 범위는 표본 시작 연도 전년 1월 1일~종료 연도 다음 해 12월 31일의 **period_end**이다. 2018~2025 표본이면 2017~2026이다. 실제 미래 시점의 원천 수치가 허용된다는 뜻은 아니다. 기존 공개시각·기간 검증은 유지된다. 기본 `sec-collect`는 범위를 명시하지 않으면 기존처럼 전체 이력을 검사한다.

```bash
python -m AI.orchestration.cli sec-collect --ciks 0000104169 --period-end-start 2017-01-01 --period-end-end 2026-12-31 --archive AI/data/bronze
```

범위 밖의 과거 관측은 적재 대상에서 제외하지만 원본에는 그대로 남고 범위 설정은 run metadata/config hash에 남는다. 범위 안의 오류는 기존대로 전체 run을 격리한다. 날짜 자체가 불명확한 행도 범위 밖이라고 추정하지 않는다. 과거 quarantine 이력은 삭제하지 않는다. 범위 변경은 기존 적재 자료를 삭제하지 않으며 watermark만으로 전체 이력 확보 여부를 판단해서는 안 된다.

`--only-uncollected`는 해당 범위를 포함한 성공 이력이 있으면 건너뛴다. 최신성 갱신용 옵션은 아니다. 새 수정값을 확인하려면 이 옵션 없이 실행한다. 검증 cohort는 scheduler 등록과 투자대상 universe 생성을 대체하지 않는다.

## 원본 대조와 공개시각 검사

```bash
python -m AI.orchestration.cli sec-validate --archive AI/data/bronze --output AI/data/validation/sec50-report.json --require-eight-years
```

보고서 파일은 덮어쓰지 않는다. 입력 cohort를 변경하려면 `--cohort <json>`을 명시한다. 검사 중 실패한 발행인도 결과에 남기며, 기술적 실패가 있으면 종료 코드 1이다. `--require-eight-years`는 모든 표본의 대상 연도별 원본 10-K 존재 여부도 종료 코드에 반영한다. 이 옵션 이름과 무관하게 연도 범위는 cohort의 start_year/end_year를 따른다.

검사 항목:

- checksum을 검증한 Bronze JSON에서 수치·태그·단위·기간·accession을 별도 조회해 저장된 모든 fact와 대조한다. ingestion parser를 재사용해 같은 결과끼리 비교하지 않는다.
- 원본 submissions의 acceptanceDateTime과 filing/fact의 공개·사용 가능 시각을 대조한다.
- 매 회계연도의 최초 원본 10-K 사용 가능 시각 직전/직후에 SDK를 조회한다. 해당 공시의 미리 노출된 fact, 미래 fact/feature를 탐지한다.
- 해당 공시 자체의 보고기간에 연결된 원천 `fy`로 회계연도를 판별한다. 52/53주 회계연도가 다음 해 1월에 끝나도 달력연도로 잘못 분류하지 않는다.
- 연도별 metric 존재 여부, 파생 feature의 값/NULL 사유, 원천 fact ID를 보존한다. feature 입력이 없을 때 0이나 추정값을 만들지 않는다.
- 매출·순이익·자산·영업현금흐름·CapEx의 검토용 표본에 기간·단위·accession·raw ID를 포함한다.

각 발행인 조회는 repeatable-read로 고정한다. 보고서는 cohort, mapping version, 검증 코드 소스 hash, 원본 checksum, 보고서 hash를 포함한다. 검증 기간의 원본 10-K가 모두 있다는 결과는 모든 metric/feature가 완전하다는 뜻이 아니다.

이 검증은 **원본 일치와 시간 계약을 자동 검사**한다. 전체 회계정의·XBRL 의미·기업별 비GAAP 차이를 독립 검토한 golden certification과는 구분하며, 보고서의 `golden_certification`은 `independent_accounting_review_pending`으로 남는다. EV/EBITDA에 필요한 검증된 과거 시가총액 등 미확보 입력도 생성하지 않는다.

## 남은 원천 이슈

- Salesforce: companyfacts가 참조하는 `0001108524-21-000014`의 접수 시각이 수집한 submissions/history에 없다. SEC archive의 원본 `.txt`와 header 경로도 확인 당시 404였다. 해당 accession과 연결된 86개 mapped fact를 추정 시각으로 적재하지 않아 회사 run 전체가 격리된다.
- Disney: 선택한 현재 CIK로 2018년 원본 연차 공시가 확인되지 않는다. 이전 법인 CIK와의 검증된 연결이 필요하다.
- Honeywell: 2020-12-31·2021-12-31 보고기간에 연결된 서로 다른 원본 10-K가 모두 `fy=2020`을 제공했다. 나중 공시를 2020년 지표로 선택하지 않고 해당 연도 선택을 차단했다. 원본 재무제표의 fiscal-year context를 검토해야 한다.
- Activision Blizzard·Seagen: 선택한 과거 CIK에서 2023~2025 원본 10-K가 없다. 이를 현재 기업의 값으로 이어 붙이지 않는다. 인수·상장폐지 이력 및 표본별 검증 기간 정책을 별도로 검토해야 한다.
- Walmart·Union Pacific·Pfizer: 전체 이력에는 검증 기간 전의 미래 종료일 또는 duration 시작일 누락 사례가 있었다. 대상 범위를 명시한 새 수집은 성공했으며 이전 실패 원본·격리는 보존했다.

실행 숫자와 feature 가용성은 함께 생성한 `SEC_VALIDATION_RESULTS.md`를 참조한다. 운영 DB migration/적재, 서버 재부팅, 과거 universe·기업행동 전수 검증은 이 검증 작업에 포함하지 않았다.
