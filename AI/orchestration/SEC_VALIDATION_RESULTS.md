# SEC 50사 검증 결과

2026-10-06 후속 계산·부채 매핑 개선 결과는 [재무 지표 v2 검증](FEATURES_V2.md), 2026-10-29 누락·충돌 보정과 서버 검증은 [보정 결과](SEC_REMEDIATION_RESULTS.md)에 기록했다. 아래는 비교 기준으로 보존한 10월 1일 결과다.

2026-10-01, 운영 DB와 분리한 로컬 PostgreSQL `ingestion_test_sec50`에서 실행했다. 대상은 2018~2025 회계연도이며 자세한 계약·실행법은 [SEC 검증 가이드](SEC_VALIDATION.md)를 참조한다.

49개 발행인의 저장된 fact를 원본 companyfacts와 대조했다. Salesforce는 접수 시각 누락으로 격리되어 저장된 fact가 없다. Honeywell의 연차 fiscal-year 충돌은 자동 선택하지 않았다. Disney의 과거 CIK 및 인수된 표본의 후속 연도는 원천 이력 검토가 필요하다.

공개 전 노출 검사는 연도별 원본 10-K 경계의 SDK 표본 검사다. 원본 수치·시간 연결 대조는 저장된 전체 fact에 적용했다. 연도별 원본 공시가 있다는 판정과 모든 파생 지표를 계산할 수 있다는 판정은 다르다. 이 보고서는 독립 회계 검토를 완료한 golden certification이 아니다.

전체 근거 JSON과 원본은 이 작업공간의 `.venv-ingestion/sec50-validation-reviewed.json`, `.venv-ingestion/sec50-bronze`에 보존했다. 아래 숫자·표는 해당 JSON에서 생성했다.

산업기업 모델을 적용한 367개 기업·회계연도 조합에서 FCF는 278개, EBITDA는 97개, 순부채는 33개, ROIC는 3개만 계산 가능했다. 따라서 다음 우선 과제는 회계정의가 확인된 태그·부채 구성 입력 확대와 기간 정렬 검토다. 현재 모델의 NULL을 0으로 바꾸거나 50사 재무 지표 완성으로 해석하면 안 된다. JPM/BAC의 16개 연도에는 산업기업 모델을 적용하지 않았다.

<!-- GENERATED RESULTS -->

## Recorded checks

| Check | Result |
|---|---|
| Issuers requested | 50 |
| Issuers with stored facts | 49 |
| Fact rows reconciled | 251,901 |
| Complete FY2018-2025 annual coverage | 45 / 50 |
| Annual release boundaries checked | 383 |
| Source value/context mismatches | 0 |
| Pre-publication exposures | 0 |
| Automated tests | 86 passed |
| Ruff | passed |

Report SHA-256: `7a1d49760134e506465ef1931f9dd63ad8b8a603aa3fbed8ea981072301595b7`

## Feature availability at annual release

| Feature | Valid | Missing inputs | Unaligned inputs | Other |
|---|---:|---:|---:|---:|
| fcf | 278 | 73 | 16 | 0 |
| ebit | 141 | 173 | 53 | 0 |
| ebitda_calculated_standard | 97 | 190 | 80 | 0 |
| total_debt | 33 | 334 | 0 | 0 |
| net_debt | 33 | 334 | 0 | 0 |
| roic | 3 | 364 | 0 | 0 |

## Issuer coverage

| Label | CIK | Fact rows | Missing / unresolved fiscal years |
|---|---|---:|---|
| AAPL | 0000320193 | 6,258 | - |
| MSFT | 0000789019 | 6,606 | - |
| AMZN | 0001018724 | 7,092 | - |
| GOOGL | 0001652044 | 3,180 | - |
| META | 0001326801 | 4,548 | - |
| NVDA | 0001045810 | 6,309 | - |
| TSLA | 0001318605 | 5,047 | - |
| AVGO | 0001730168 | 3,107 | - |
| ORCL | 0001341439 | 5,341 | - |
| ADBE | 0000796343 | 6,463 | - |
| CRM | 0001108524 | 0 | 2018, 2019, 2020, 2021, 2022, 2023, 2024, 2025 |
| INTC | 0000050863 | 5,783 | - |
| AMD | 0000002488 | 5,981 | - |
| QCOM | 0000804328 | 5,809 | - |
| CSCO | 0000858877 | 6,571 | - |
| IBM | 0000051143 | 5,638 | - |
| TXN | 0000097476 | 6,032 | - |
| AMAT | 0000006951 | 6,750 | - |
| MU | 0000723125 | 5,595 | - |
| NFLX | 0001065280 | 6,142 | - |
| DIS | 0001744489 | 2,261 | 2018 |
| CMCSA | 0001166691 | 5,994 | - |
| KO | 0000021344 | 5,305 | - |
| PEP | 0000077476 | 5,813 | - |
| PG | 0000080424 | 5,561 | - |
| WMT | 0000104169 | 3,053 | - |
| COST | 0000909832 | 6,667 | - |
| HD | 0000354950 | 5,662 | - |
| MCD | 0000063908 | 5,935 | - |
| NKE | 0000320187 | 4,693 | - |
| XOM-historical | 0000034088 | 4,056 | - |
| CVX | 0000093410 | 4,500 | - |
| COP | 0001163165 | 4,251 | - |
| SLB | 0000087347 | 5,558 | - |
| CAT | 0000018230 | 5,178 | - |
| DE | 0000315189 | 5,737 | - |
| GE | 0000040545 | 5,389 | - |
| BA | 0000012927 | 5,874 | - |
| HON | 0000773840 | 5,408 | 2020, 2021 |
| UPS | 0001090727 | 5,651 | - |
| UNP | 0000100885 | 2,545 | - |
| JNJ | 0000200406 | 5,417 | - |
| PFE | 0000078003 | 2,719 | - |
| MRK | 0000310158 | 4,794 | - |
| ABBV | 0001551152 | 4,474 | - |
| AMGN | 0000318154 | 5,503 | - |
| JPM | 0000019617 | 3,932 | - |
| BAC | 0000070858 | 4,043 | - |
| ATVI-historical | 0000718877 | 4,555 | 2023, 2024, 2025 |
| SGEN-historical | 0001060736 | 3,121 | 2023, 2024, 2025 |
