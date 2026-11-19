# SEC 누락·충돌 보정 결과 — 2026-10-29

50개 표본의 누락과 부채 충돌을 원문 단위로 재검토했다. 운영 DB에는 쓰지 않았고 로컬 검증 DB와 서버의 일회성 `ingestion_test_codex_*` DB에서만 실행했다.

## 해결된 문제

- Salesforce FY2021: 현재 SEC 개별 filing URL에서 사라진 원문을 SEC 공식 2021-03-17 daily feed에서 복구했다. daily feed에는 접수 시각이 없어 `accepted_at`을 임의 생성하지 않는다. `availability_basis=sec_feed_day_end`와 함께 미국 동부시간 기준 해당 날짜 종료 후 60초인 `2021-03-18T04:01:00Z`부터 노출한다.
- Honeywell FY2021과 Salesforce FY2026: 표지·결산일과 전자 fiscal-year 태그가 충돌한 공시다. 체크섬으로 고정한 원문, CIK, accession, 결산일, 기존 잘못된 FY가 모두 정확히 일치할 때만 검토 연도를 적용한다.
- Disney FY2018: 현재 CIK의 전신 CIK `0001001039`를 명시적인 identity window로 연결했다. 원본 fact의 CIK는 바꾸지 않는다.
- Activision Blizzard와 Seagen: 각각 Microsoft와 Pfizer에 인수된 뒤의 FY2023~2025를 독립 연차 공시 누락으로 세지 않는다. 실제 공시가 없는 상태는 그대로 표시하며 `not_applicable_after_acquisition`으로 구분한다.
- 부채 충돌: 액면가·계약상 원금과 장부가, 금융리스 포함 여부, Chevron의 단기→장기 재분류를 원문으로 구분했다. 검토한 기업·결산일·입력값이 모두 같을 때만 장부가 구성요소에서 리스를 차감한다. 확인되지 않은 새 값은 다시 `conflicting_debt_totals`가 된다.
- 입력 누락: 국내+해외 세전이익, 비영업 이자비용, 리스 포함 부채에서 명시된 금융리스 차감 경로를 추가했다. 서로 다른 회계 정의를 단순 alias로 합치지 않는다.
- 스냅샷: `data_as_of` 검증을 PostgreSQL 서버 시계에 맞췄다. 클라이언트와 서버의 작은 시계 차이로 방금 저장한 자료가 사라지는 문제를 막았다.

## 최종 50사 결과

최종 보고서는 `.venv-ingestion/sec50-remediation-reviewed-20261028.json`이다.

| 검사 | 결과 |
|---|---:|
| 기술 실패 기업 | 0 / 50 |
| 기대 연차 자료 충족 | 50 / 50 |
| 실제 8개 연차 공시 보유 | 48 / 50 |
| 원본 대조 fact | 269,324 |
| 공시 경계 검사 | 394 |
| unresolved 부채 충돌 | 0 |
| 로컬 PostgreSQL 자동 테스트 | 106 passed |

실제 8개 연차 공시 보유가 48개인 이유는 ATVI와 SGEN의 인수 후 연도를 가짜 데이터로 채우지 않았기 때문이다. 따라서 이 결과는 “모든 기업에 8년 숫자가 존재한다”는 의미가 아니다.

동일한 기존 산업기업 367개 기업·연도 조합에서 valid 수는 다음과 같다. 기존 valid가 invalid/NULL로 바뀐 회귀는 FCF, EBIT, EBITDA, 총부채, 순부채에 0건이다. ROIC는 전년도 부채 충돌도 검토한 뒤 회귀 0건이다.

| 지표 | v2 valid | 보정 후 valid |
|---|---:|---:|
| FCF | 290 | 290 |
| EBIT | 169 | 267 |
| EBITDA | 164 | 201 |
| 총부채 | 49 | 61 |
| 순부채 | 45 | 55 |
| ROIC | 9 | 16 |

전체 기대 연차 범위는 378개 기업·연도 조합이며 valid는 FCF 301, EBIT 270, EBITDA 210, 총부채 63, 순부채 57, ROIC 16이다. 나머지는 원문에 필요한 정의의 입력이 없거나 기간·세율·자본 조건을 충족하지 않은 경우다. `.venv-ingestion/feature-gap-inventory-20261028.json`에 기업·연도·지표별 `missing_metrics`와 `input_issues`를 보존했다. NULL을 0으로 바꾸지 않았다.

보고서 SHA-256 식별값은 `eb6e896829e88833ea8f9eceea9c9d6282a2fa9e3142d31463a38ca49ccf7273`이다. 원문과 회계 정의에 대한 독립 외부 검토 완료를 뜻하지 않는다.

## 서버 검증

설정된 PostgreSQL 16.13 서버에 무작위 이름의 임시 DB를 만들고 migration 1~5를 적용한 뒤, MSFT·AMZN·CRM·CVX·HON·ABBV 실제 SEC 자료 33,697건을 COPY하여 원본 대조와 PIT 경계를 검사했다. 기술 실패 0건, 기대 연차 범위 6/6이었다. 서비스 DB의 `public`/`ingestion` schema fingerprint는 전후 동일했고 임시 DB를 제거했다. 실행 결과는 `.venv-ingestion/server-remediation-final-20261029.json`에 저장했다.

서버 터널이 끊기면 완료로 간주하지 않는다. 테스트 스크립트는 서비스 DB의 테이블을 생성·수정·삭제하지 않는다.
