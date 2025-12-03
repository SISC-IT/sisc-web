// src/pages/QuantTradingDashboard.jsx
import React, { useEffect, useState } from 'react';
import './QuantTradingDashboard.css';
import TradeCard from '../components/quantbot/TradeCard';
import ReportModal from '../components/quantbot/ReportModal';
import { api } from '../utils/axios.js';

// Recharts
import {
  LineChart,
  Line,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';

// --- Summary Cards: /api/quant-bot/portfolio-overview 사용 ---
function SummaryCards({ overview, loading, error }) {
  let cumulativeReturnText = '-';
  let periodText = '-';
  let totalAssetText = '-';
  let initialCapitalText = '-';

  if (loading) {
    cumulativeReturnText = '로딩 중...';
    periodText = '로딩 중...';
    totalAssetText = '로딩 중...';
    initialCapitalText = '';
  } else if (error) {
    cumulativeReturnText = '에러';
    periodText = '에러';
    totalAssetText = '에러';
    initialCapitalText = error;
  } else if (overview) {
    const startDate = overview.startDate;
    const endDate = overview.endDate;
    const initialCapital = Number(overview.initialCapital ?? 0);
    const lastTotalAsset = Number(overview.lastTotalAsset ?? 0);

    let rate = 0;
    if (initialCapital > 0) {
      rate = (lastTotalAsset / initialCapital - 1) * 100;
    }

    cumulativeReturnText = `${rate.toFixed(2)}%`;
    periodText = `${startDate} ~ ${endDate}`;
    totalAssetText = `₩ ${Math.round(lastTotalAsset).toLocaleString()}원`;
    initialCapitalText = `초기자본 ${Math.round(
      initialCapital
    ).toLocaleString()}원`;
  }

  return (
    <div className="summary-grid">
      <div className="summary-card">
        <div className="summary-label">누적 수익률</div>
        <div className="summary-main summary-value-highlight">
          {cumulativeReturnText}
        </div>
        <div className="summary-sub">포트폴리오 전체 기준</div>
      </div>

      <div className="summary-card">
        <div className="summary-label">매매 기간</div>
        <div className="summary-main">{periodText}</div>
        <div className="summary-sub">{initialCapitalText}</div>
      </div>

      <div className="summary-card">
        <div className="summary-label">총자산</div>
        <div className="summary-main">{totalAssetText}</div>
        <div className="summary-sub">{initialCapitalText}</div>
      </div>
    </div>
  );
}

// --- Holdings: /api/quant-bot/positions 사용 ---
// (지금은 안 쓰지만 나중에 분리하고 싶으면 이 컴포넌트로 빼서 쓸 수 있음)
function HoldingsList({ positions, loading, error }) {
  const safeList = Array.isArray(positions) ? positions : [];

  return (
    <div className="holdings-card">
      <div className="section-title">보유 주식</div>

      {loading && <div>로딩 중...</div>}
      {!loading && error && <div style={{ color: 'red' }}>{error}</div>}

      {safeList.length === 0 && !loading && !error && (
        <div style={{ color: '#6b7280' }}>보유 포지션이 없습니다.</div>
      )}

      {safeList.map((p) => (
        <div className="holding-item" key={p.ticker}>
          <div>
            <div className="holding-symbol">{p.ticker}</div>
            <div className="holding-shares">{p.positionQty}주</div>
          </div>
          <div className="holding-right">
            <div className="holding-amount">
              {Number(p.marketPrice).toLocaleString()}원
            </div>
            <div className="holding-pnl">
              {p.pnl >= 0 ? '+' : ''}
              {Number(p.pnl).toLocaleString()}원 ({p.pnlRate}%)
            </div>
          </div>
        </div>
      ))}
    </div>
  );
}

// --- 전략 수익 곡선: /api/quant-bot/assets 사용 ---
function StrategyEquityChart({
  assets,
  loading,
  error,
  onDateSelect,
  clickableDates = [], // 매매 로그 있는 날짜 리스트
}) {
  const safeList = Array.isArray(assets) ? assets : [];

  const data = safeList
    .map((a) => {
      const dateStr = a.date ?? a.date;
      const d = dateStr ? new Date(dateStr) : null;
      const ymd = d
        ? `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(
            2,
            '0'
          )}-${String(d.getDate()).padStart(2, '0')}`
        : '';

      return {
        date: ymd,
        totalAsset: Number(a.totalAsset ?? a.total_asset ?? 0),
      };
    })
    .filter((d) => d.date)
    .sort((a, b) => (a.date > b.date ? 1 : -1));

  const hasLogsOnDate = (date) =>
    Array.isArray(clickableDates) && clickableDates.includes(date);

  const handleChartClick = (state) => {
    if (!state || !state.activeLabel) return;

    const date = state.activeLabel;

    // ✅ 매매 로그 있는 날짜만 클릭 동작
    if (
      Array.isArray(clickableDates) &&
      clickableDates.length > 0 &&
      !clickableDates.includes(date)
    ) {
      // //console.log('해당 날짜에는 매매 로그가 없습니다:', date);
      return;
    }

    if (typeof onDateSelect === 'function') {
      onDateSelect(date);
    }
  };

  return (
    <div className="chart-card">
      <div className="section-title">
        전략 수익 곡선{' '}
        <span
          style={{
            fontSize: '12px',
            color: '#6b7280',
            opacity: 0.6,
            cursor: 'pointer',
          }}
        >
          (클릭 시 해당 매매 로그로 이동)
        </span>
      </div>

      {loading && (
        <div className="chart-placeholder">자산 데이터를 불러오는 중...</div>
      )}

      {!loading && error && (
        <div className="chart-placeholder" style={{ color: '#dc2626' }}>
          자산 데이터 조회 중 오류가 발생했습니다: {error}
        </div>
      )}

      {!loading && !error && data.length === 0 && (
        <div className="chart-placeholder">표시할 자산 데이터가 없습니다.</div>
      )}

      {!loading && !error && data.length > 0 && (
        <div className="chart-wrapper">
          <ResponsiveContainer width="100%" height={220}>
            <LineChart
              data={data}
              margin={{ top: 10, right: 16, bottom: 4, left: 0 }}
              onClick={handleChartClick} // 👈 클릭 시 날짜 선택
            >
              <CartesianGrid strokeDasharray="3 3" />
              <XAxis dataKey="date" tick={{ fontSize: 11 }} />
              <YAxis
                tick={{ fontSize: 11 }}
                tickFormatter={(v) => `${Math.round(v).toLocaleString()}`}
              />
              <Tooltip
                content={({ active, payload, label }) => {
                  if (!active || !payload || payload.length === 0) return null;

                  const value = payload[0].value;
                  const hasLogs = hasLogsOnDate(label);

                  return (
                    <div
                      style={{
                        background: 'white',
                        border: '1px solid #e5e7eb',
                        borderRadius: 4,
                        padding: '8px 10px',
                        fontSize: 12,
                        color: '#111827',
                        boxShadow: '0 2px 4px rgba(0,0,0,0.08)',
                      }}
                    >
                      <div
                        style={{
                          marginBottom: 4,
                          fontWeight: 600,
                        }}
                      >
                        날짜: {label}
                      </div>
                      <div>
                        총자산:{' '}
                        {Number.isFinite(value)
                          ? Math.round(value).toLocaleString()
                          : value}
                        원
                      </div>
                      <div
                        style={{
                          marginTop: 4,
                          color: hasLogs ? '#16a34a' : '#6b7280',
                        }}
                      >
                        {hasLogs ? '매매 로그 있음' : '매매 로그 없음'}
                      </div>
                    </div>
                  );
                }}
              />
              <Line
                type="monotone"
                dataKey="totalAsset"
                stroke="#2563eb"
                strokeWidth={2}
                dot={false}
                activeDot={{ r: 4 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </div>
  );
}

// --- History Section: /api/quant-bot/logs 사용 (현재는 미사용이지만 남겨둠) ---
function HistorySection({ trades, loading, error }) {
  return (
    <div className="history-section">
      <div className="history-header">
        <div className="history-title">과거 이력</div>
        <div className="history-filter">
          <select className="select">
            <option>최근 매매</option>
          </select>
          <select className="select">
            <option>전체 수익 구간</option>
          </select>
        </div>
      </div>

      {loading && <div>매매 로그를 불러오는 중입니다...</div>}

      {!loading && error && (
        <div style={{ color: '#dc2626', fontSize: 12 }}>
          매매 로그 조회 중 오류가 발생했습니다: {error}
        </div>
      )}

      {!loading && !error && trades.length === 0 && (
        <div style={{ fontSize: 13, color: '#6b7280' }}>
          아직 기록된 매매 로그가 없습니다.
        </div>
      )}

      {!loading &&
        !error &&
        trades.map((trade) => <TradeCard key={trade.id} trade={trade} />)}
    </div>
  );
}

export default function QuantTradingDashboard() {
  const [positions, setPositions] = useState([]);
  const [trades, setTrades] = useState([]);

  const [modalOpen, setModalOpen] = useState(false);
  const [reportData, setReportData] = useState(null);

  // ✅ 포트폴리오 요약 정보 상태
  const [overview, setOverview] = useState(null);
  const [overviewLoading, setOverviewLoading] = useState(false);
  const [overviewError, setOverviewError] = useState(null);

  // ✅ 자산 곡선 상태
  const [assets, setAssets] = useState([]);
  const [assetsLoading, setAssetsLoading] = useState(false);
  const [assetsError, setAssetsError] = useState(null);

  // ✅ 매매 로그 날짜 필터 상태
  const [availableDates, setAvailableDates] = useState([]); // ['2025-11-26', ...]
  const [selectedDate, setSelectedDate] = useState('ALL'); // 'ALL' 또는 특정 날짜

  // --- 0️⃣ 포트폴리오 요약 API ---
  useEffect(() => {
    const fetchOverview = async () => {
      // console.log(
      //   '📌 [overview] 요청 시작 → GET /api/quant-bot/portfolio-overview'
      // );
      setOverviewLoading(true);
      setOverviewError(null);

      try {
        const res = await api.get('/api/quant-bot/portfolio-overview');
        // console.log('📌 [overview] 응답 코드:', res.status);
        // console.log('📌 [overview] 응답 데이터:', res.data);

        if (!res.data) {
          setOverview(null);
        } else {
          setOverview(res.data);
        }
      } catch (err) {
        console.error('❌ [overview] 요청 실패:', err);
        const msg =
          err?.message ||
          err?.data?.message ||
          '포트폴리오 정보를 불러오지 못했습니다.';
        setOverviewError(msg);
        setOverview(null);
      } finally {
        setOverviewLoading(false);
      }
    };

    fetchOverview();
  }, []);

  // --- 0.5️⃣ 자산 곡선 API ---
  useEffect(() => {
    const fetchAssets = async () => {
      // console.log('📌 [assets] 요청 시작 → GET /api/quant-bot/assets');
      setAssetsLoading(true);
      setAssetsError(null);

      try {
        const res = await api.get('/api/quant-bot/assets');
        // console.log('📌 [assets] 응답 코드:', res.status);
        // console.log('📌 [assets] 응답 데이터:', res.data);

        if (Array.isArray(res.data)) {
          setAssets(res.data);
        } else {
          console.warn('⚠️ assets 응답이 배열이 아님:', res.data);
          setAssets([]);
        }
      } catch (err) {
        console.error('❌ [assets] 요청 실패:', err);
        const msg =
          err?.message ||
          err?.data?.message ||
          '자산 데이터를 불러오지 못했습니다.';
        setAssetsError(msg);
        setAssets([]);
      } finally {
        setAssetsLoading(false);
      }
    };

    fetchAssets();
  }, []);

  // --- 1️⃣ 포지션 API ---
  useEffect(() => {
    const fetchPositions = async () => {
      // console.log('📌 [positions] 요청 시작 → GET /api/quant-bot/positions');

      try {
        const res = await api.get('/api/quant-bot/positions');

        // console.log('📌 [positions] 응답 코드:', res.status);
        // console.log('📌 [positions] 응답 데이터:', res.data);

        if (Array.isArray(res.data)) {
          setPositions(res.data);
        } else {
          console.warn('⚠️ positions 응답이 배열이 아님:', res.data);
          setPositions([]);
        }
      } catch (err) {
        console.error('❌ [positions] 요청 실패:', err);
        setPositions([]);
      }
    };

    fetchPositions();
  }, []);

  // --- 2️⃣ 매매 로그 API ---
  useEffect(() => {
    const fetchLogs = async () => {
      // console.log('📌 [logs] 요청 시작 → GET /api/quant-bot/logs');

      try {
        const res = await api.get('/api/quant-bot/logs');

        // console.log('📌 [logs] 응답 코드:', res.status);
        // console.log('📌 [logs] 응답 데이터:', res.data);

        if (Array.isArray(res.data)) {
          setTrades(res.data);

          // fillDate 기준으로 유니크 날짜 리스트 생성
          const dates = Array.from(
            new Set(res.data.map((t) => t.fillDate).filter(Boolean))
          ).sort((a, b) => a.localeCompare(b));

          setAvailableDates(dates);
        } else {
          console.warn('⚠️ logs 응답이 배열이 아님:', res.data);
          setTrades([]);
          setAvailableDates([]);
        }
      } catch (err) {
        console.error('❌ [logs] 요청 실패:', err);
        setTrades([]);
        setAvailableDates([]);
      }
    };

    fetchLogs();
  }, []);

  // --- 3️⃣ 리포트 불러오기 ---
  const handleReportClick = async (id) => {
    // console.log(
    //   `📌 [report] 요청 시작 → GET /api/quant-bot/report?executionId=${id}`
    // );

    setModalOpen(true);
    setReportData(null);

    try {
      const res = await api.get('/api/quant-bot/report', {
        params: { executionId: id },
      });

      // console.log('📌 [report] 응답 코드:', res.status);
      // console.log('📌 [report] 응답 데이터:', res.data);

      setReportData(res.data);
    } catch (err) {
      console.error('❌ [report] 요청 실패:', err);

      setReportData({
        ticker: '',
        signal: '',
        price: '',
        date: '',
        report: '리포트를 불러오는 데 실패했습니다.',
      });
    }
  };

  // ✅ 날짜 필터 적용된 매매 로그
  const filteredTrades =
    selectedDate === 'ALL'
      ? trades
      : trades.filter((t) => t.fillDate === selectedDate);

  return (
    <main className="main-content-only">
      <h1 className="page-title">퀀트 트레이딩</h1>

      {/* ✅ 포트폴리오 요약 카드 */}
      <SummaryCards
        overview={overview}
        loading={overviewLoading}
        error={overviewError}
      />

      {/* 포지션 + 전략 수익 곡선 */}
      <div className="content-row">
        <div className="holdings-card">
          <div className="section-title">현재 포지션</div>

          {positions.length === 0 && (
            <div style={{ color: '#6b7280' }}>보유 포지션이 없습니다.</div>
          )}

          {positions.map((p) => (
            <div className="holding-item" key={p.ticker}>
              <div>
                <div className="holding-symbol">{p.ticker}</div>
                <div className="holding-shares">{p.positionQty}주</div>
              </div>
              <div className="holding-right">
                <div className="holding-amount">
                  {Number(p.marketPrice).toLocaleString()}원
                </div>
                <div className="holding-pnl">
                  {Number(p.pnl) >= 0 ? '+' : ''}
                  {Number(p.pnl).toLocaleString()}원 ({p.pnlRate}%)
                </div>
              </div>
            </div>
          ))}
        </div>

        <StrategyEquityChart
          assets={assets}
          loading={assetsLoading}
          error={assetsError}
          onDateSelect={(date) => setSelectedDate(date)} // 차트 클릭 → 날짜 선택
          clickableDates={availableDates} // 매매 로그 있는 날짜만 클릭/표시
        />
      </div>

      {/* 매매 로그 + 날짜 필터 */}
      <div className="history-section">
        <div className="history-header">
          <div className="history-title">매매 로그</div>
          <div className="history-filter">
            <select
              className="select"
              value={selectedDate}
              onChange={(e) => setSelectedDate(e.target.value)}
            >
              <option value="ALL">전체 날짜</option>
              {availableDates.map((d) => (
                <option key={d} value={d}>
                  {d}
                </option>
              ))}
            </select>
          </div>
        </div>

        {filteredTrades.length === 0 && (
          <div style={{ color: '#6b7280' }}>
            {trades.length === 0
              ? '매매 로그가 없습니다.'
              : '선택한 날짜의 매매 로그가 없습니다.'}
          </div>
        )}

        {filteredTrades.map((trade) => (
          <TradeCard
            key={trade.id}
            trade={trade}
            onReportClick={handleReportClick}
          />
        ))}
      </div>

      {/* 모달 */}
      <ReportModal
        open={modalOpen}
        onClose={() => setModalOpen(false)}
        report={reportData}
      />
    </main>
  );
}
