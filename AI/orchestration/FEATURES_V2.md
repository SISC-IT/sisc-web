# 재무 지표 v2 검증 — 2026-10-06

운영 DB와 분리한 `ingestion_test_sec50`에서 기존 SEC 원문을 재처리했다. 49개 재처리 모두 성공했고 새로운 LongTermDebt fact 2,207개를 추가했다. 기존 fact 식별자와 원문을 보존했다. 운영 반영·배포는 하지 않았다.

## 변경 계약

- TTM은 최신 기간의 공개된 350~380일 연간 총액을 우선 사용한다. 53주 회계연도를 포함한다. 더 최신 분기가 있으면 오래된 연간 총액으로 대체하지 않는다. 부분 수정이 연간 공시보다 늦으면 연간 총액을 재사용하지 않으며 최신 기간까지 이어지는 분기 체인이 필요하다. 동순위 연간 값·기간 충돌은 NULL이다.
- 재무상태표 기준일은 total_assets를 기준으로 선택한다. 자산 입력이 없으면 주식수를 제외한 시점형 입력을 사용한다. 결산 이후의 주식수 측정일이 부채·현금을 숨기지 않는다.
- `LongTermDebt`는 유동성 대체분을 포함하는 별도 `long_term_debt_including_current` 지표다. 단기차입금과 더하며 유동성 장기부채를 다시 더하지 않는다. 기존 구성요소 합계와 충돌하면 NULL이다. 누락된 단기차입금을 0으로 간주하지 않는다.
- SEC 원문에 포함된 태그 설명을 확인했다. 리스가 포함된 `DebtCurrent`, 감모상각이 포함된 총액, 지분법 손익 포함 여부가 다른 세전이익은 기존 정의의 대체 태그로 추가하지 않았다.
- 매핑 전체 버전은 `sec_us_gaap_v2`다. 기존 개별 지표의 정의 버전과 source key는 v1을 유지하고 신규 지표만 v2를 사용한다. `financial_features` 계산 버전은 v2로 구분한다.

## 실제 표본 결과

비교 기준은 [10월 1일 보고서](SEC_VALIDATION_RESULTS.md)의 동일한 산업기업 367개 기업·회계연도 조합이다. JPM/BAC의 16개 연도는 산업기업 계산에서 제외했다.

| 지표 | 기존 valid | v2 valid | v2 missing | v2 unaligned | 기타 |
|---|---:|---:|---:|---:|---:|
| FCF | 278 | 290 | 64 | 13 | 0 |
| EBIT | 141 | 169 | 145 | 53 | 0 |
| EBITDA | 97 | 164 | 119 | 84 | 0 |
| 총부채 | 33 | 49 | 315 | 0 | 충돌 3 |
| 순부채 | 33 | 45 | 322 | 0 | 0 |
| ROIC | 3 | 9 | 356 | 0 | 세율·자본·정렬 조건 실패 2 |

기존 valid가 NULL로 바뀐 경우는 MSFT FY2018, AMZN FY2024·2025의 총부채·순부채다. 나머지 지표에는 valid→비정상 회귀가 없다. 아래는 원본 장기부채 구성요소 비교이며 단위는 USD million이다. 반올림·장부가 조정 여부를 추측해 상쇄하지 않았다.

| 표본 | 비유동 + 유동 장기부채 | 보고된 장기부채 총액 |
|---|---:|---:|
| MSFT 2018 | 72,242 + 3,998 = 76,240 | 76,200 |
| AMZN 2024 | 52,623 + 5,017 = 57,640 | 58,000 |
| AMZN 2025 | 65,648 + 2,748 = 68,396 | 68,836 |

양쪽 모두 valid인 사례 중 FCF 3개, EBIT 28개, EBITDA 31개의 값이 바뀌었다. 분기 합산 대신 해당 시점의 보고된 연간 총액을 사용한 결과이며 단순 누락 보충만 수행한 것은 아니다. 예를 들어 META FY2023 EBITDA는 57,943→59,055 USD million이다. 새 연간 입력은 세전이익 47,428 + 이자 446 + 감가상각 11,020 + 무형자산상각 161이며 각 원본 fact ID가 결과에 남는다. 독립 회계 검토 완료를 의미하지 않는다.

254,108개 fact 원문 대조에서 수치·기간 불일치는 0건, 383개 연차 공시 경계 검사에서 공시 전 노출은 0건이었다. FY2018~2025 연차 자료 충족 기업은 여전히 45/50개다. CRM 접수 시각 누락, HON 회계연도 충돌, DIS 과거 CIK와 인수기업 ATVI/SGEN 후속 연도 문제는 남아 있다. `--require-eight-years` 검증은 이 미충족 조건 때문에 종료 코드 1을 반환한다.

## 재처리와 재현 근거

```powershell
python -m AI.orchestration.cli --db TEST_DB sec-replay --run-id <원본-run-UUID> --archive .venv-ingestion/sec50-bronze
python -m AI.orchestration.cli --db TEST_DB sec-validate --archive .venv-ingestion/sec50-bronze --output .venv-ingestion/sec50-features-v2-reviewed.json --require-eight-years
```

`sec-replay`는 체크섬과 archive root를 확인한 기존 원문만 읽는다. SEC 수집과 같은 issuer lock을 사용하며 별도 `sec_replay` 실행 이력에 기록한다. 신규 다운로드, 원문 덮어쓰기, 수집 watermark 갱신은 없다. 재실행은 기존 fact를 중복 삽입하지 않는다. 손상 원문은 격리한다.

근거 파일은 로컬 `.venv-ingestion/sec50-features-v2-reviewed.json`, `.venv-ingestion/feature-v2-replay.json`, `.venv-ingestion/sec50-bronze`에 보존했다. 보고서의 canonical report hash는 `4e82b5f39cc43008bd7437354026ec797b0809b6f4238d0fcc627279fb3511c6`이다.

PostgreSQL 포함 자동 테스트 94개 통과. CI와 동일한 Ruff `--isolated --select E4,E7,E9,F,I` 검사 통과. 저장소 기본 전체 Ruff 규칙에는 기존 코드 등을 포함한 40개 진단이 남아 있으므로 전체 규칙 통과로 해석하지 않는다. 연간 TTM·53주·부분 수정·공시 전 차단·부채 충돌·원문 훼손·재처리 멱등성·watermark 보존을 검사했다.
