# 세종투자연구회 웹사이트 프로젝트
<img width="225" height="225" alt="image" src="https://github.com/user-attachments/assets/6b4be44f-6f50-4b49-a7ee-047340004a21" />

### 1. 세종투자연구회 웹사이트 개발

게시물 관리 기능
- 기존 네이버 카페 대체

출석체크 기능
- 기존의 수기 체크 방식을 쉽게 함

모의 트레이딩 게임
- 주식에 쉽게 관심을 가질 수 있게 하기 위한 주가 맞추기 미니 게임

백테스팅 툴
- 다양한 주식 지표, 차트 분석시 사용되는 기준들을 조건으로 하여 자신만의 전략을 짤 수 있게 함

포인트 로직
- 출석체크 등에서 포인트를 지급하여, 동아리 참여 확대를 위해 도입

### 2. 빅데이터와 AI를 활용한 모의 퀀트 트레이딩 봇 개발
- 국제 금융 지수 ex) CPI,금리 / 주가와 주가 지표등을 활용
- 기존의 모델들을 학습하고 비교, 분석하여 목표에 어울리는 모델 선정과 데이터 수집 및 정제
- 데일리 트레이딩을 하여 수익율, 매수/매도한 종목, 투자 근거등을 서비스 사용자에게 제공

### 3. 국내 기업 공시 실적 수집 및 조회 (`disclosure-research`)
국내 상장기업의 OpenDART 정기보고서(사업·반기·분기보고서)에서 최근 4개 회계분기 실적(매출액·영업이익·당기순이익)을 수집·저장하고 조회하는 백엔드 기능입니다.

#### 구현 기능
- **정기보고서 기반 분기 실적 수집 배치** (`DisclosureEarningsCollectionService`, `DisclosureCollectionRunner`)
  - OpenDART 기업개황(`/company.json`)의 `acc_mt`로 결산월을 확인하고, 정기공시 목록(`/list.json`, `pblntf_ty=A`, `last_reprt_at=N`) 전체 페이지를 조회합니다.
  - 최초 실행 시 최신 정기보고서 기준 최근 연속 4개 회계분기를 적재하며, 이후 재실행 시 신규 분기만 추가하고 동일 접수번호 중복 적재를 방지합니다.
  - 정정공시는 별도 접수번호로 이력을 보존하며, 원본 공시와의 관계가 확인된 경우 해당 분기 실적에 반영합니다.
  - 재무제표(`/fnlttSinglAcntAll.json`)는 연결(`CFS`)을 우선 조회하고 연결재무제표가 없는 경우 별도(`OFS`)를 사용합니다.
  - 손익계산서(`IS`/`CIS`)에서 XBRL 표준계정 ID 우선순위로 매출액·영업이익·당기순이익을 추출하며, 실제 `0`과 결측(`null`)을 구분합니다.
  - 4분기 실적은 동일 회계연도·회계 기준·통화의 연간값(`11011`)에서 3분기 누적값(`11014`)을 차감해 산출하며, 비교 근거가 불충분하거나 사후 정정으로 비교 불가한 경우 `null`로 처리합니다.
  - 재무 API 미반영 분기는 `pending`으로 남겨 다음 실행 시 재조회합니다.
- **단일 기업 최근 4분기 실적 조회 API** (`DisclosureEarningsController`, `DisclosureEarningsQueryService`)
  - `GET /api/disclosures/companies/{corpCode}/financials` (정회원 이상 `TEAM_MEMBER` 권한 필요)
  - 분기별 회계연도·분기·결산연월, 연결/별도 기준, 통화, 매출액·영업이익·당기순이익(정밀도 보존 십진 문자열, 결측 `null`), DART 원문 링크를 반환합니다.
  - 잘못된 8자리 고유번호 형식은 `400`, 미저장 기업은 `404`를 반환합니다.
- **DB 스키마 (PostgreSQL / Flyway)** (`V8__disclosure_research.sql`, `disclosure-local/V1__disclosure_local_schema.sql`)
  - `disclosure_company`: DART 고유번호(`dart_corp_code` unique), 종목코드, 기업명, 법인구분, 결산월(`closing_month`)
  - `disclosure_filing`: 접수번호(`dart_rcept_no` unique), 보고서명, 접수일, 회계연도, 보고서 코드, 원본 `jsonb`
  - `disclosure_financial_metric`: 지표 유형, 계정 ID/명, 회계 기준(`CFS`/`OFS`), 기간 유형(`CURRENT_PERIOD`/`ANNUAL`/`YEAR_TO_DATE`), 금액(`numeric(30,4)`), 통화, `unique nulls not distinct` 중복 방지 제약

#### 실행 및 테스트
- **단위 테스트**
  ```powershell
  cd backend
  .\gradlew.bat test --tests "org.sejongisc.backend.disclosure.*"
  ```
- **로컬 수동 수집 실행** (루트 `.env`에 `OPENDART_API_KEY`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 설정 필요)
  ```powershell
  .\backend\scripts\collect-disclosures-local.ps1 -CorpCode 00126380
  ```
