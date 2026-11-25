# 공시 리서치 (`disclosure`)
> 기준일: 2026-11-25 | 정책: 확정 | 구현: 완료

## 1. 범위 및 역할
- **담당:** OpenDART 정기 분기 실적(매출·영업이익·순이익) 및 4대 이벤트 공시(수주·주주환원·CAPEX·잠정실적) 규칙 기반 수집·저장, 200개 추적 기업 목록(미확인 빨간 점 포함) 및 기업 상세 조회 API·UI 제공
- **제외:** AI/LLM 기반 요약, 공시 원문 HTML/XML의 DB 영구 저장 (`dart_url` 링크로 대체)

## 2. 입출력 흐름 및 예외 상태
1. **정기 실적 수집 (`DisclosureEarningsCollectionService`)**: 기업 결산월 조회 → 정기보고서 목록 조회 → 최초 4분기(이후 누적) 대상 선별 → DB 미저장 분기만 재무제표(`CFS` 우선, `OFS` 폴백) 수집·저장
2. **이벤트 공시 수집 (`DisclosureEventCollectionService`)**: 자사주 취득 전용 JSON(`/tsstkAqDecsn.json`) + 거래소공시(`pblntf_ty=I`) 목록 조회 → DB 미저장 공시만 `/document.xml` 원문 표 파싱(`DartDocumentTableParser`) → 규칙 필터링(`DisclosureEventRuleEngine`) → 저장
3. **통합 조회 (`DisclosureQueryService` / `DisclosureController`)**:
   - `GET /api/disclosures/companies`: 추적 기업 목록(최대 200개) + `hasUnread`(빨간 점) 반환
   - `GET /api/disclosures/companies/{corpCode}?type=`: 진입 시 `disclosure_user_read` 원자적 Upsert(읽음 처리) + 분기 실적 추이(4분기 역산 포함) + 이벤트 스프레드시트 반환
- **필수 처리 상태:** `013`(데이터 없음) 정상 빈 결과 처리, 부분 실패 시 기수집 분기 보존, 동시 더블클릭 시 `ON CONFLICT DO UPDATE`로 유니크 충돌 방어

## 3. 정책 및 필터 규칙 (Non-AI)
- **확정 규칙 (`DisclosureEventRuleEngine`):**

| 카테고리 (`DisclosureEventCategory`) | OpenDART 소스 | 기본 필터 기준 |
| :--- | :--- | :--- |
| `ORDER_CONTRACT` (수주 공시) | `pblntf_ty=I` + `/document.xml` | 조선·건설·특수장비(`311`, `41`, `42`, `292`): 매출대비 `15%↑` 또는 `1,000억↑`<br>일반 업종: 매출대비 `10%↑` 또는 `100억↑` (유보·해지 포함) |
| `SHAREHOLDER_RETURN` (주주환원) | `/tsstkAqDecsn.json` + `pblntf_ty=I` | 자사주 소각 전체, 자사주 취득 `100억↑`, 시가배당률 `3.5%↑` 또는 배당총액 `500억↑` |
| `CAPEX` (신규시설투자) | `pblntf_ty=I` + `/document.xml` | `disclosure_company.track_capex = true` 기업(삼성전자·SK하이닉스 기본 활성) 중 자기자본대비 `5%↑` 또는 `300억↑` |
| `PRELIMINARY_EARNINGS` (잠정 실적) | `pblntf_ty=I` + `/document.xml` | 영업이익 YoY `±20%↑` 또는 흑자전환·적자전환 |

- **4분기 단독 실적 공식 (`QuarterlyEarningsCalculator`):** `4분기 = 사업보고서(연간) - 3분기보고서(1~3분기 누적)`
- **빨간 점 (`hasUnread`) 공식:** `max(최신 이벤트 collected_at, 최신 정기보고서 collected_at) > disclosure_user_read.last_viewed_at`
- **CAPEX 확장 정책:** 추후 현대차 등 추가 시 코드 수정 없이 `disclosure_company.track_capex = true` 업데이트

## 4. 연결 포인터 (인터페이스 계약)
| 대상 | 입력 | 출력 |
| :--- | :--- | :--- |
| `GET /api/disclosures/companies` | `q`, `page`, `size` (기본 200), JWT | `CompanyListResponse` (기업 목록 + `hasUnread`) |
| `GET /api/disclosures/companies/{corpCode}` | `corpCode`(8자리), `type`(선택), JWT | `CompanyDetailResponse` (최근 분기 + 전체 추이 + 이벤트 목록) |
| 프론트엔드 (`/board/disclosures`) | `DisclosureBoard.jsx` ↔ `disclosureApi.js` | 기업 목록(빨간 점) + 기업 상세(실적 표 + 필터 스프레드시트) |

## 5. 운영 및 로컬 QA 가드레일
- **필수 환경변수:** `OPENDART_API_KEY` (GitHub Environments `development` / `production` 시크릿 등록 및 워크플로우 `.env` 주입 필요)
- **단건 QA 수집 실행 (로컬):**
  ```powershell
  .\backend\scripts\collect-disclosures-local.ps1 -CorpCode 00164779
  ```
- **주의사항:** 공시 원문 HTML은 DB에 저장하지 않으며, 에러 로그에 `crtfc_key`가 노출되지 않도록 `OpenDartClientException`으로 마스킹한다.

## 6. 구현 상태 및 완료 기준
- **백엔드:** `controller/DisclosureController`, `service/(DisclosureEarningsCollectionService, DisclosureEventCollectionService, DisclosureQueryService)`, `service/enums/*`, `service/settings/*`, `entity/*`, `repository/*`, Flyway `V12`·`V13` 구현 완료
- **프론트엔드:** `pages/DisclosureBoard.jsx`, `pages/DisclosureBoard.module.css`, `utils/disclosureApi.js`, `App.jsx`, `Sidebar.jsx` 연동 완료
- **검증:** `cd backend && .\gradlew.bat test --tests "org.sejongisc.backend.disclosure.*"` (17/17 통과)
