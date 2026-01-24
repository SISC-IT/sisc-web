----------------------------------------------------------------------
-- Schema: public
--  - 퀀트 트레이딩 / 백테스트 / XAI / 포트폴리오 관리용 메인 스키마
----------------------------------------------------------------------

CREATE SCHEMA "public";
CREATE SCHEMA "neon_auth";

----------------------------------------------------------------------
-- 1. price_data
--    - 개별 종목의 일별 시세(OHLCV)를 저장하는 기본 원천 테이블
--    - 모든 시계열 데이터의 기준 축 (기술지표, 펀더멘털, 공매도 등)
--    - (date, ticker) 기준으로 하루 1행 보장
----------------------------------------------------------------------

CREATE TABLE "price_data" (
"adjusted_close" numeric(38, 2),   -- 조정 종가 (배당/액분/병합 반영)
"close" numeric(38, 2),            -- 종가
"date" date,                       -- 거래일 기준 날짜
"high" numeric(38, 2),             -- 고가
"low" numeric(38, 2),              -- 저가
"open" numeric(38, 2),             -- 시가
"volume" bigint,                   -- 거래량 (체결 주식 수)
"ticker" varchar(255),             -- 종목 티커 (확장성 고려, 자르지 않음)
CONSTRAINT "price_data_pkey" PRIMARY KEY("date","ticker")
);

----------------------------------------------------------------------
-- 2. technical_indicators
--    - price_data 기반으로 계산된 기술적 지표 저장
--    - ticker + date 기준으로 price_data와 1:1 매핑
--    - 모델 feature 소스로 활용
----------------------------------------------------------------------

CREATE TABLE "technical_indicators" (
"ticker" varchar(10),              -- 종목 티커
"date" date,                       -- 기준 날짜
"rsi" numeric(18, 6),              -- RSI
"macd" numeric(18, 6),             -- MACD
"bollinger_bands_upper" numeric(18, 6), -- 볼린저 밴드 상단
"bollinger_bands_lower" numeric(18, 6), -- 볼린저 밴드 하단
"atr" numeric(18, 6),              -- Average True Range
"obv" numeric(18, 6),              -- On-Balance Volume
"stochastic" numeric(18, 6),       -- 스토캐스틱 오실레이터
"mfi" numeric(18, 6),              -- Money Flow Index
"ma_5" numeric(18, 6),             -- 5일 이동평균
"ma_20" numeric(18, 6),            -- 20일 이동평균
"ma_50" numeric(18, 6),            -- 50일 이동평균
"ma_200" numeric(18, 6),           -- 200일 이동평균
CONSTRAINT "technical_indicators_pkey" PRIMARY KEY("ticker","date")
);

----------------------------------------------------------------------
-- 3. macroeconomic_indicators
--    - 날짜 단위 거시경제 지표 저장
--    - 모든 종목에 공통 적용되는 글로벌 변수
--    - price_data와는 date 기준으로 조인
----------------------------------------------------------------------

CREATE TABLE "macroeconomic_indicators" (
"date" date PRIMARY KEY,           -- 기준 날짜 (발표일 등)
"cpi" numeric(18, 2),               -- 소비자물가지수 (CPI)
"gdp" numeric(18, 2),               -- GDP
"ppi" numeric(18, 2),               -- 생산자물가지수 (PPI)
"jolt" numeric(18, 2),              -- JOLTs
"cci" numeric(18, 2),               -- 소비자신뢰지수
"interest_rate" numeric(18, 2),     -- 기준금리 / 연방기금금리
"trade_balance" numeric(18, 2),     -- 무역수지
"core_cpi" numeric,                 -- 근원 CPI
"real_gdp" numeric,                 -- 실질 GDP
"unemployment_rate" numeric,        -- 실업률
"consumer_sentiment" numeric,       -- 소비자심리지수
"ff_targetrate_upper" numeric,      -- FOMC 목표금리 상단
"ff_targetrate_lower" numeric,      -- FOMC 목표금리 하단
"pce" numeric,                      -- 개인소비지출 물가지수
"core_pce" numeric,                 -- 근원 PCE
"tradebalance_goods" numeric,       -- 상품 무역수지
"trade_import" numeric,             -- 수입
"trade_export" numeric              -- 수출
);

----------------------------------------------------------------------
-- 4. company_fundamentals
--    - 개별 종목의 펀더멘털(재무제표 수치) 저장
--    - 분기/연간 데이터를 특정 date에 매핑
--    - price_data와 결합해 펀더멘털 + 가격 분석
----------------------------------------------------------------------

CREATE TABLE "company_fundamentals" (
"ticker" varchar(10),               -- 종목 티커
"date" date,                        -- 기준 날짜 (보고서/발표 기준)
"revenue" numeric(30, 6),            -- 매출액
"net_income" numeric(30, 6),         -- 순이익
"total_assets" numeric(30, 6),       -- 총자산
"total_liabilities" numeric(30, 6),  -- 총부채
"equity" numeric(30, 6),             -- 자본
"eps" numeric(18, 6),                -- 주당순이익 (EPS)
"pe_ratio" numeric(18, 6),           -- 주가수익비율 (P/E)
CONSTRAINT "company_fundamentals_pkey" PRIMARY KEY("ticker","date")
);

----------------------------------------------------------------------
-- 5. short_interest
--    - 공매도 관련 데이터 저장
--    - 수급/포지셔닝 보조 지표
----------------------------------------------------------------------

CREATE TABLE "short_interest" (
"ticker" varchar(10),               -- 종목 티커
"date" date,                        -- 기준 날짜
"short_interest" double precision,  -- 공매도 비율 또는 잔고
"short_volume" bigint,              -- 공매도 거래량
CONSTRAINT "short_interest_pkey" PRIMARY KEY("ticker","date")
);

----------------------------------------------------------------------
-- 6. xai_reports
--    - XAI 모듈이 생성한 설명 가능한 자연어 리포트 저장
--    - (ticker, date, signal) 기준으로 1개 리포트만 유지
----------------------------------------------------------------------

CREATE TABLE "xai_reports" (
"id" bigserial PRIMARY KEY,          -- XAI 리포트 고유 ID
"ticker" varchar(255) NOT NULL,      -- 종목 티커
"signal" varchar(255) NOT NULL,      -- 신호 (BUY / SELL / HOLD)
"price" numeric(38, 2) NOT NULL,     -- 신호 발생 시점 가격
"date" date NOT NULL,                -- 신호 발생 날짜
"report" text,                       -- 자연어 설명 리포트
"created_at" timestamp DEFAULT CURRENT_TIMESTAMP NOT NULL, -- 생성 시각
"run_id" varchar(64),                -- 생성된 실행(run) 식별자
CONSTRAINT "uq_xai_reports_ticker_date_signal"
UNIQUE("ticker","date","signal"),
CONSTRAINT "ck_xai_reports_signal"
CHECK ((signal)::text = ANY (ARRAY['BUY','SELL','HOLD']))
);

----------------------------------------------------------------------
-- 7. executions
--    - 백테스트 / 실험 실행 시 발생한 체결 로그
--    - 전략 검증, 성과 분석의 핵심 데이터
----------------------------------------------------------------------

CREATE TABLE "executions" (
"id" bigserial PRIMARY KEY,           -- 체결 로그 ID
"run_id" varchar(64),                 -- 실행 ID
"ticker" varchar(255) NOT NULL,       -- 종목 티커
"signal_date" date NOT NULL,          -- 신호 발생일
"signal_price" numeric(38, 2),        -- 신호 당시 가격
"signal" varchar(255) NOT NULL,       -- 신호 타입
"fill_date" date NOT NULL,            -- 체결일
"fill_price" numeric(38, 2) NOT NULL, -- 체결가
"qty" integer NOT NULL,               -- 체결 수량
"side" varchar(255) NOT NULL,         -- BUY / SELL
"value" numeric(38, 2) NOT NULL,      -- 체결 금액
"commission" numeric(38, 2) NOT NULL, -- 수수료
"cash_after" numeric(38, 2) NOT NULL, -- 체결 후 현금
"position_qty" integer NOT NULL,      -- 체결 후 보유 수량
"avg_price" numeric(38, 2) NOT NULL,  -- 평균 매입가
"pnl_realized" numeric(38, 2) NOT NULL, -- 실현 손익
"pnl_unrealized" numeric(38, 2) NOT NULL, -- 미실현 손익
"created_at" timestamp with time zone DEFAULT now() NOT NULL, -- 기록 시각
"xai_report_id" bigint                -- 연결된 XAI 리포트 ID
);

ALTER TABLE "executions"
ADD CONSTRAINT "fk_executions_xai_reports"
FOREIGN KEY ("xai_report_id")
REFERENCES "xai_reports"("id");

----------------------------------------------------------------------
-- 8. portfolio_positions
--    - 현재 시점 기준 종목별 포지션 스냅샷
--    - 실시간/웹 조회용
----------------------------------------------------------------------

CREATE TABLE "portfolio_positions" (
"id" bigserial PRIMARY KEY,
"ticker" varchar(20) NOT NULL,        -- 종목 티커
"position_qty" integer NOT NULL,      -- 보유 수량
"avg_price" numeric(18, 6) NOT NULL,  -- 평균 매입가
"current_price" numeric(18, 6) NOT NULL, -- 현재가
"market_value" numeric(20, 6) NOT NULL, -- 평가금액
"pnl_unrealized" numeric(20, 6) NOT NULL, -- 미실현 손익
"pnl_realized_cum" numeric(20, 6) NOT NULL, -- 누적 실현 손익
"updated_at" timestamp with time zone DEFAULT now() -- 갱신 시각
);

----------------------------------------------------------------------
-- 9. portfolio_summary
--    - 계좌 전체 기준 일별 요약 (equity curve)
----------------------------------------------------------------------

CREATE TABLE "portfolio_summary" (
"date" date PRIMARY KEY,              -- 기준 날짜
"total_asset" numeric(20, 6) NOT NULL,-- 총 자산
"cash" numeric(20, 6) NOT NULL,       -- 현금
"market_value" numeric(20, 6) NOT NULL,-- 평가금액 합
"pnl_unrealized" numeric(20, 6) NOT NULL,-- 미실현 손익
"pnl_realized_cum" numeric(20, 6) NOT NULL,-- 누적 실현 손익
"initial_capital" numeric(20, 6) NOT NULL,-- 시작 자본
"return_rate" numeric(10, 6) NOT NULL,-- 수익률
"created_at" timestamp with time zone DEFAULT now() -- 기록 시각
);

---------------------------------------------------------------------- 
-- 10. stock_info
--    - 종목의 섹터, 산업, 시가총액 등 정적인 정보를 저장
--    - 종목별 기본 정보를 제공하고, 필터링 및 분석에 활용
----------------------------------------------------------------------

CREATE TABLE public.stock_info (
    ticker VARCHAR(20) PRIMARY KEY,       -- 종목 티커
    sector VARCHAR(100),                  -- 대분류 (예: Technology)
    industry VARCHAR(200),                -- 세부 분류 (예: Consumer Electronics)
    market_cap BIGINT,                    -- 시가총액 (필터링용)
    updated_at TIMESTAMP DEFAULT NOW()    -- 마지막 갱신 시각
);

----------------------------------------------------------------------
-- 11. neon_auth.users_sync
--    - Neon 인증 시스템과 동기화된 사용자 정보
--    - raw_json 기반으로 주요 필드를 generated column으로 추출
----------------------------------------------------------------------

CREATE TABLE "neon_auth"."users_sync" (
"raw_json" jsonb NOT NULL,             -- 원본 사용자 JSON
"id" text PRIMARY KEY GENERATED ALWAYS AS ((raw_json ->> 'id')) STORED,
"name" text GENERATED ALWAYS AS ((raw_json ->> 'display_name')) STORED,
"email" text GENERATED ALWAYS AS ((raw_json ->> 'primary_email')) STORED,
"created_at" timestamp with time zone
GENERATED ALWAYS AS (
to_timestamp(
trunc(((raw_json ->> 'signed_up_at_millis')::bigint)::double precision / 1000)
)
) STORED,
"updated_at" timestamp with time zone,
"deleted_at" timestamp with time zone
);
