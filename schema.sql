----------------------------------------------------------------------
-- Schema: public
-- 퀀트 트레이딩, 백테스트, XAI 분석 및 포트폴리오 관리 시스템의 핵심 데이터 모델
-- AI 스키마 현황입니다. 주요 테이블과 인덱스가 포함되어 있으며, 향후 필요에 따라 추가/수정될 수 있습니다.
-- 구조만 보여줄 뿐, 이 문서를 변경해도 db 자체는 변경되지 않습니다!
-- 스키마 변경시 최신화 바랍니다.
----------------------------------------------------------------------

-- 1. HDD 경로를 테이블스페이스로 등록
CREATE TABLESPACE ts_ai_hdd LOCATION '/var/lib/postgresql/ai_data';

----------------------------------------------------------------------
-- 1. stock_info / company_names
--    - 종목의 기본 정보 및 메타데이터 관리
----------------------------------------------------------------------
CREATE TABLE "company_names" (
    "company_name" varchar(100) PRIMARY KEY, -- 기업 한글 정식 명칭
    "ticker" varchar(255) NOT NULL UNIQUE    -- 종목 티커
) TABLESPACE ts_ai_hdd;

CREATE TABLE "stock_info" (
    "ticker" varchar(20) PRIMARY KEY,  -- 종목 티커
    "sector" varchar(100),             -- 섹터 분류 (예: IT, 금융)
    "industry" varchar(200),           -- 세부 산업 분류
    "market_cap" bigint,               -- 시가총액 (필터링/비중 계산용)
    "fmp_completed" boolean DEFAULT false,        -- FMP 재무제표 데이터 수집 완료 여부
    "updated_at" timestamp DEFAULT now() -- 정보 갱신 일시
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 2. price_data
--    - 개별 주식 종목의 일별 시세(OHLCV) 및 거래 정보를 저장
--    - 기술적 분석 및 모델 Feature 엔지니어링의 기본 원천 데이터
----------------------------------------------------------------------
CREATE TABLE "price_data" (
    "adjusted_close" numeric(38, 2),   -- 조정 종가 (배당, 액면분할 등이 반영된 주가)
    "close" numeric(38, 2),            -- 당일 종가
    "date" date,                       -- 거래 일자
    "high" numeric(38, 2),             -- 당일 고가
    "low" numeric(38, 2),              -- 당일 저가
    "open" numeric(38, 2),             -- 당일 시가
    "volume" bigint,                   -- 일일 거래량 (주식 수)
    "ticker" varchar(255),             -- 종목 티커 심볼
    "per" numeric(18, 6),              -- 주가수익비율 (PER)
    "pbr" numeric(18, 6),              -- 주가순자산비율 (PBR)
    "amount" numeric(38, 2),           -- 일일 거래대금
    CONSTRAINT "price_data_pkey" PRIMARY KEY("date", "ticker")
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 3. crypto_price_data
--    - 암호화폐(가상자산)의 시계열 가격 및 마켓 데이터를 저장
--    - 주식 데이터와 분리하여 자산군별 특화 분석 수행
----------------------------------------------------------------------
CREATE TABLE "crypto_price_data" (
    "ticker" varchar(20),              -- 코인/토큰 티커
    "date" timestamp,                  -- 거래 시점 (타임스탬프)
    "open" numeric(38, 8),             -- 시가
    "high" numeric(38, 8),             -- 고가
    "low" numeric(38, 8),              -- 저가
    "close" numeric(38, 8),            -- 종가
    "volume" numeric(38, 8),           -- 거래량
    "market_cap" numeric(38, 2),       -- 시가총액
    CONSTRAINT "crypto_price_data_pkey" PRIMARY KEY("date", "ticker")
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 4. macroeconomic_indicators (거시경제 지표)
--    - 국가별 거시경제 지표 및 시장 위험 지표(VIX 등) 저장
--    - 시장 국면(Regime) 판단 및 멀티모달 AI 모델의 외부 변수로 활용
----------------------------------------------------------------------
CREATE TABLE "macroeconomic_indicators" (
    "date" date PRIMARY KEY,           -- 지표 발표 또는 기준 일자
    "cpi" numeric(18, 2),              -- 소비자물가지수 (CPI)
    "gdp" numeric(18, 2),              -- 국내총생산 (GDP)
    "ppi" numeric(18, 2),              -- 생산자물가지수 (PPI)
    "jolt" numeric(18, 2),             -- 구인/이직 보고서 (JOLTs) 수치
    "cci" numeric(18, 2),              -- 소비자신뢰지수 (CCI)
    "interest_rate" numeric(18, 2),    -- 기준 금리
    "trade_balance" numeric(18, 2),    -- 무역수지
    "core_cpi" numeric,                -- 근원 소비자물가지수
    "real_gdp" numeric,                -- 실질 GDP
    "unemployment_rate" numeric,       -- 실업률
    "consumer_sentiment" numeric,      -- 소비자심리지수
    "ff_targetrate_upper" numeric,     -- 연방기금금리 상단 목표치
    "ff_targetrate_lower" numeric,     -- 연방기금금리 하단 목표치
    "pce" numeric,                     -- 개인소비지출(PCE) 물가지수
    "core_pce" numeric,                -- 근원 PCE
    "tradebalance_goods" numeric,      -- 상품무역수지
    "trade_import" numeric,            -- 수입액
    "trade_export" numeric,            -- 수출액
    "us10y" numeric(10, 4),            -- 미국채 10년물 금리
    "us2y" numeric(10, 4),             -- 미국채 2y 금리
    "yield_spread" numeric(10, 4),     -- 장단기 금리차 (10Y-2Y)
    "vix_close" numeric(10, 2),        -- 변동성 지수(VIX) 종가
    "dxy_close" numeric(10, 4),        -- 달러 인덱스 종가
    "wti_price" numeric(10, 2),        -- WTI 유가
    "gold_price" numeric(10, 2),       -- 국제 금 가격
    "credit_spread_hy" numeric(10, 4)  -- 하이일드 채권 신용 스프레드
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 5. company_fundamentals (제무제표 데이터)
--    - 종목별 재무제표 수치 및 주요 투자 보조 지표 저장
--    - 가치 투자 전략 및 퀀트 팩터 모델의 핵심 소스
----------------------------------------------------------------------
CREATE TABLE "company_fundamentals" (
    "ticker" varchar(255),             -- 종목 티커
    "date" date,                       -- 데이터 기준일 (분기/연간 보고서 발표일 등)
    "revenue" numeric(30, 6),          -- 매출액
    "net_income" numeric(30, 6),       -- 당기순이익
    "total_assets" numeric(30, 6),     -- 총자산
    "total_liabilities" numeric(30, 6),-- 총부채
    "equity" numeric(30, 6),           -- 자본총계
    "eps" numeric(18, 6),              -- 주당순이익 (EPS)
    "roe" numeric(18, 6),              -- 자기자본이익률 (ROE)
    "debt_ratio" numeric(18, 6),       -- 부채비율
    "operating_cash_flow" numeric(30, 6), -- 영업활동현금흐름
    "interest_coverage" numeric(10, 2),  -- 이자보상배율
    "shares_issued" numeric(20, 2),      -- 유통 주식 수
    CONSTRAINT "company_fundamentals_pkey" PRIMARY KEY("ticker","date")
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 6. Market Breadth Stats (마켓 상태 지표)
--    - 전 종목 대상 통계 데이터 저장
--    - NH-NL (신고가-신저가), MA200 상회 비율 등
----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS "market_breadth" (
    "date" date PRIMARY KEY,
    
    -- 1. 신고가 - 신저가 지수 (Net High - Net Low)
    -- 52주 신고가 종목 수 - 52주 신저가 종목 수
    "nh_nl_index" integer, 
    
    -- 2. 200일 이동평균선 상회 비율 (Market Momemtum)
    -- (현재가 > MA200 인 종목 수) / 전체 종목 수 * 100
    "ma200_pct" numeric(5, 2), 
    
    "created_at" timestamp DEFAULT now()
) TABLESPACE ts_ai_hdd;

-- 인덱스
CREATE INDEX IF NOT EXISTS "idx_market_breadth_date" ON "market_breadth" ("date") TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 7. news_sentiment
--    - 비정형 데이터(뉴스/기사)를 분석한 정량적 감성 점수 저장
--    - 시장 심리 및 이벤트 드리븐 전략에 활용
----------------------------------------------------------------------
CREATE TABLE "news_sentiment" (
    "id" bigserial PRIMARY KEY,        -- 감성 분석 로그 ID
    "date" date NOT NULL,              -- 뉴스 기준 날짜
    "ticker" varchar(20) NOT NULL,     -- 관련 종목 티커
    "sentiment_score" numeric(5, 4),   -- 감성 점수 (-1 ~ 1)
    "impact_score" numeric(5, 4),      -- 시장 영향력 점수
    "risk_keyword_cnt" integer,        -- 위험 키워드 빈도수
    "article_count" integer,           -- 분석된 기사 수
    "created_at" timestamp DEFAULT now() -- 분석 기록 생성 시각
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 8. xai_reports
--    - AI 모델이 생성한 의사결정 근거(텍스트)와 매매 신호를 저장
--    - 투자자가 모델의 판단을 이해할 수 있도록 설명 가능성(Explainability) 제공
----------------------------------------------------------------------
CREATE TABLE "xai_reports" (
    "id" bigserial PRIMARY KEY,        -- XAI 리포트 고유 ID
    "ticker" varchar(255) NOT NULL,    -- 종목 티커
    "signal" varchar(255) NOT NULL,    -- 발생 신호 (BUY, SELL, HOLD)
    "price" numeric(38, 2) NOT NULL,   -- 신호 발생 당시 가격
    "date" date NOT NULL,              -- 신호 발생 날짜
    "report" text,                     -- LLM/XAI 기반 자연어 리포트 본문
    "created_at" timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 리포트 생성 시각
    "run_id" varchar(64),              -- 분석 실행 프로세스 식별 ID
    CONSTRAINT "uq_xai_reports_ticker_date_signal" UNIQUE("ticker","date","signal"),
    CONSTRAINT "ck_xai_reports_signal" CHECK (signal = ANY (ARRAY['BUY', 'SELL', 'HOLD']))
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 9. account_names
--    - 파이프라인/계정 식별자 메타데이터 관리
--    - executions.account_id 외래키 참조 기준 테이블
----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS "account_names" (
    "id" bigserial PRIMARY KEY,                  -- 계정 식별자 PK
    "account_code" varchar(128) NOT NULL,       -- 파이프라인에서 참조하는 안정 키
    "account_name" varchar(255) NOT NULL,       -- 표시용 계정 이름
    "description" text,                          -- 선택 설명
    "is_active" boolean DEFAULT true NOT NULL,  -- 활성 여부
    "created_at" timestamp with time zone DEFAULT now() NOT NULL, -- 생성 시각
    "updated_at" timestamp with time zone DEFAULT now() NOT NULL, -- 수정 시각
    CONSTRAINT "uq_account_names_account_code" UNIQUE ("account_code")
) TABLESPACE ts_ai_hdd;

-- 기본 legacy 파이프라인 계정을 idempotent하게 등록
INSERT INTO "account_names" (
    "account_code",
    "account_name",
    "description",
    "is_active"
) VALUES (
    'chart_based_signal_model',
    '차트 기반 시그널 모델',
    'Chart-based signal model account.',
    true
)
ON CONFLICT ("account_code") DO UPDATE
SET
    "account_name" = EXCLUDED."account_name",
    "description" = EXCLUDED."description",
    "is_active" = true,
    "updated_at" = now();

----------------------------------------------------------------------
-- 10. executions
--    - 실제 거래 또는 백테스트 시뮬레이션에서 발생한 개별 체결 이력
--    - 자산 추적 및 성과 평가를 위한 가장 세밀한 로그 데이터
----------------------------------------------------------------------
CREATE TABLE "executions" (
    "id" bigserial PRIMARY KEY,        -- 체결 로그 ID
    "run_id" varchar(64),              -- 백테스트/실행 회차 ID
    "account_id" bigint,               -- 체결 주체 계정 ID (account_names.id)
    "ticker" varchar(255) NOT NULL,    -- 종목 티커
    "signal_date" date NOT NULL,       -- 전략 신호 발생일
    "signal_price" numeric(38, 2),     -- 전략 신호 당시 가격
    "signal" varchar(255) NOT NULL,    -- 발생한 신호 타입
    "fill_date" date NOT NULL,         -- 실제 주문 체결일
    "fill_price" numeric(38, 2) NOT NULL, -- 실제 체결 가격
    "qty" integer NOT NULL,            -- 체결 수량
    "side" varchar(255) NOT NULL,      -- 매수/매도 구분 (BUY/SELL)
    "value" numeric(38, 2) NOT NULL,   -- 총 체결 금액
    "commission" numeric(38, 2) NOT NULL, -- 거래 수수료
    "cash_after" numeric(38, 2) NOT NULL, -- 체결 후 잔여 현금
    "position_qty" integer NOT NULL,   -- 체결 후 총 보유 수량
    "avg_price" numeric(38, 2) NOT NULL, -- 체결 후 평균 매입가
    "pnl_realized" numeric(38, 2) NOT NULL, -- 해당 거래로 확정된 실현 손익
    "pnl_unrealized" numeric(38, 2) NOT NULL, -- 현재 시점 기준 미실현 손익
    "created_at" timestamp with time zone DEFAULT now() NOT NULL, -- DB 기록 시각
    "xai_report_id" bigint,            -- 연관된 XAI 리포트 참조 ID
    CONSTRAINT "fk_executions_account_names" FOREIGN KEY ("account_id") REFERENCES "account_names"("id") ON DELETE RESTRICT,
    CONSTRAINT "fk_executions_xai_reports" FOREIGN KEY ("xai_report_id") REFERENCES "xai_reports"("id") ON DELETE SET NULL
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 11. portfolio_summary
--    - 전체 자산의 일별 성과 요약 (Equity Curve 생성용)
----------------------------------------------------------------------
CREATE TABLE "portfolio_summary" (
    "date" date PRIMARY KEY,           -- 기준 날짜
    "total_asset" numeric(20, 6) NOT NULL, -- 총 자산 (현금 + 평가금액)
    "cash" numeric(20, 6) NOT NULL,     -- 보유 현금
    "market_value" numeric(20, 6) NOT NULL, -- 보유 주식 총 평가금액
    "pnl_unrealized" numeric(20, 6) NOT NULL, -- 전체 미실현 손익
    "pnl_realized_cum" numeric(20, 6) NOT NULL, -- 누적 확정 실현 손익
    "initial_capital" numeric(20, 6) NOT NULL, -- 투자 원금
    "return_rate" numeric(10, 6) NOT NULL, -- 누적 수익률
    "created_at" timestamp with time zone DEFAULT now() -- 기록 시각
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 12. portfolio_positions
--      날짜별 종목 포지션 스냅샷을 저장
--    - portfolio_summary(일자별 총자산)와 함께 일별 상태 재현/검증 가능
----------------------------------------------------------------------

CREATE TABLE "portfolio_positions" (
    "date" date NOT NULL,                   -- 스냅샷 기준 날짜
    "ticker" varchar(255) NOT NULL,         -- 종목 티커
    "position_qty" integer NOT NULL,        -- 보유 수량
    "avg_price" numeric(38, 6) NOT NULL,    -- 평균 매입가
    "current_price" numeric(38, 6) NOT NULL,-- 기준일 종가/평가 단가
    "market_value" numeric(20, 6) NOT NULL, -- 평가금액
    "pnl_unrealized" numeric(20, 6) NOT NULL,-- 미실현 손익
    "pnl_realized_cum" numeric(20, 6) NOT NULL, -- 누적 실현 손익(해당 종목)
    "created_at" timestamp with time zone DEFAULT now() NOT NULL -- 기록 시각
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 13. event_calendar
--    - 주요 경제 일정(FOMC, CPI, GDP) 및 기업 실적 발표일 저장
--    - AI 모델의 'Event' 피처(D-Day 계산 등)를 위한 원천 데이터
----------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS "event_calendar" (
    "id" bigserial PRIMARY KEY,
    "event_date" date NOT NULL,        -- 이벤트 예정일 (YYYY-MM-DD)
    "event_type" varchar(50) NOT NULL, -- 이벤트 타입 ('FOMC', 'CPI', 'EARNINGS', 'GDP' 등)
    "ticker" varchar(20),              -- 관련 티커 (거시지표는 'MACRO' 저장)
    "description" text,                -- 상세 설명 (예: 'FOMC Rate Decision', 'AAPL Earnings')
    "forecast" numeric(18, 5), -- 시장 예측치 (Consensus)
    "actual" numeric(18, 5), -- 실제 발표치
    "created_at" timestamp DEFAULT now(), -- 레코드 생성 시각
    
    -- 중복 방지: 같은 날짜, 같은 타입, 같은 대상(티커)의 이벤트는 중복될 수 없음
    CONSTRAINT "uq_event_calendar" UNIQUE ("event_date", "event_type", "ticker")
) TABLESPACE ts_ai_hdd;

----------------------------------------------------------------------
-- 14. sector_returns
--    - stock_info의 'sector'와 매핑되는 ETF의 일별 수익률 저장
--    - Wide Format이 아닌 Long Format (Date, Sector) 구조
----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS "sector_returns" (
    "date" date NOT NULL,               -- 기준 일자
    "sector" varchar(100) NOT NULL,     -- 섹터명 (stock_info의 sector와 일치, 예: 'Technology')
    "etf_ticker" varchar(20),           -- 대표 ETF 티커 (예: 'XLK')
    "return" numeric(10, 6),            -- 일일 등락률 (0.0123 = 1.23%)
    "close" numeric(10, 2),             -- ETF 종가 (참고용)
    "created_at" timestamp DEFAULT now(),
    
    -- PK: 날짜와 섹터의 조합은 유일해야 함
    CONSTRAINT "pk_sector_returns" PRIMARY KEY ("date", "sector")
) TABLESPACE ts_ai_hdd;

-- 1. price_data: 종목별 시세 히스토리 단독 조회용
CREATE INDEX IF NOT EXISTS "idx_price_data_ticker" ON "price_data" ("ticker") TABLESPACE ts_ai_hdd;
CREATE INDEX IF NOT EXISTS "idx_crypto_price_data_ticker" ON "crypto_price_data" ("ticker") TABLESPACE ts_ai_hdd;

-- 2. executions: 특정 백테스트 회차의 체결 내역 모아보기
CREATE INDEX IF NOT EXISTS "idx_executions_run_id" ON "executions" ("run_id") TABLESPACE ts_ai_hdd;

-- 2-1. executions: 계정 단위 분리 조회/기간 조회
CREATE INDEX IF NOT EXISTS "idx_executions_account_id" ON "executions" ("account_id") TABLESPACE ts_ai_hdd;
CREATE INDEX IF NOT EXISTS "idx_executions_account_id_fill_date" ON "executions" ("account_id", "fill_date") TABLESPACE ts_ai_hdd;

-- 3. executions: XAI 리포트 조인용 (외래키는 자동 인덱스 생성이 안 됨)
CREATE INDEX IF NOT EXISTS "idx_executions_xai_report_id" ON "executions" ("xai_report_id") TABLESPACE ts_ai_hdd;

-- 4. news_sentiment: 특정 종목의 감성 점수 필터링
CREATE INDEX IF NOT EXISTS "idx_news_sentiment_ticker_date" ON "news_sentiment" ("ticker", "date") TABLESPACE ts_ai_hdd;

-- 5. portfolio_positions: PK 누락 보완 및 날짜/종목 조회용
CREATE INDEX IF NOT EXISTS "idx_portfolio_positions_date_ticker" ON "portfolio_positions" ("date", "ticker") TABLESPACE ts_ai_hdd;

-- 6. xai_reports: 특정 분석 실행(run_id)에 대한 리포트 조회용
CREATE INDEX IF NOT EXISTS "idx_xai_reports_run_id" ON "xai_reports" ("run_id") TABLESPACE ts_ai_hdd;

-- 7. company_fundamentals: 종목별 재무제표 데이터 조회용 (날짜 기준)
CREATE INDEX IF NOT EXISTS "idx_company_fundamentals_date" ON "company_fundamentals" ("date") TABLESPACE ts_ai_hdd;
