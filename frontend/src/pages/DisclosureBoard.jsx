import React, { useState, useEffect, useCallback } from 'react';
import {
  getDisclosureCompanies,
  getDisclosureCompanyDetail,
} from '../utils/disclosureApi';
import styles from './DisclosureBoard.module.css';

const EVENT_FILTERS = [
  { id: 'ALL', label: '전체' },
  { id: 'ORDER_CONTRACT', label: '수주공시' },
  { id: 'SHAREHOLDER_RETURN', label: '주주환원' },
  { id: 'CAPEX', label: 'CAPEX' },
  { id: 'PRELIMINARY_EARNINGS', label: '잠정실적' },
];

const formatBasis = (basis) => {
  if (basis === 'CFS') return 'CFS(연결)';
  if (basis === 'OFS') return 'OFS(별도)';
  return basis || '-';
};

const formatWon = (raw) => {
  if (raw === null || raw === undefined || raw === '') return '-';
  const num = Number(raw);
  if (Number.isNaN(num)) return String(raw);
  const abs = Math.abs(num);
  const sign = num < 0 ? '-' : '';
  if (abs >= 1e12) {
    return `${sign}${(abs / 1e12).toFixed(2).replace(/\.?0+$/, '')}조`;
  }
  if (abs >= 1e8) {
    return `${sign}${Math.round(abs / 1e8).toLocaleString()}억`;
  }
  return `${sign}${Math.round(abs).toLocaleString()}원`;
};

const DisclosureBoard = () => {
  const [searchInput, setSearchInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [companies, setCompanies] = useState([]);
  const [totalCount, setTotalCount] = useState(0);
  const [listLoading, setListLoading] = useState(false);
  const [listError, setListError] = useState('');

  const [selectedCorpCode, setSelectedCorpCode] = useState(null);
  const [eventFilter, setEventFilter] = useState('ALL');
  const [detail, setDetail] = useState(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState('');

  const fetchCompanies = useCallback(async () => {
    setListLoading(true);
    setListError('');
    try {
      const data = await getDisclosureCompanies({ q: keyword, page: 0, size: 200 });
      setCompanies(data?.items || []);
      setTotalCount(data?.totalCount || 0);
    } catch {
      setListError('기업 목록을 불러오지 못했습니다.');
      setCompanies([]);
      setTotalCount(0);
    } finally {
      setListLoading(false);
    }
  }, [keyword]);

  useEffect(() => {
    fetchCompanies();
  }, [fetchCompanies]);

  const fetchDetail = useCallback(async (corpCode, filterType) => {
    if (!corpCode) return;
    setDetailLoading(true);
    setDetailError('');
    try {
      const data = await getDisclosureCompanyDetail(corpCode, filterType);
      setDetail(data);
      setCompanies((prev) =>
        prev.map((item) =>
          item.corpCode === corpCode ? { ...item, hasUnread: false } : item
        )
      );
    } catch {
      setDetailError('기업 상세 공시 데이터를 불러오지 못했습니다.');
      setDetail(null);
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (selectedCorpCode) {
      fetchDetail(selectedCorpCode, eventFilter);
    }
  }, [selectedCorpCode, eventFilter, fetchDetail]);

  const handleSelectCompany = (corpCode) => {
    setSelectedCorpCode(corpCode);
    setEventFilter('ALL');
  };

  const handleBackToList = () => {
    setSelectedCorpCode(null);
    setDetail(null);
    setDetailError('');
  };

  if (selectedCorpCode) {
    return (
      <div className={styles.container}>
        <div className={styles.topRow}>
          <button
            type="button"
            className={styles.backButton}
            onClick={handleBackToList}
          >
            ← 기업 목록으로
          </button>
        </div>

        {detailLoading && !detail ? (
          <div className={styles.statusText}>기업 상세 데이터를 불러오는 중...</div>
        ) : detailError ? (
          <div className={styles.statusText}>{detailError}</div>
        ) : detail ? (
          <>
            <header className={styles.detailHeader}>
              <h1 className={styles.title}>
                {detail.companyName}{' '}
                <span className={styles.codeMeta}>
                  {detail.stockCode || detail.corpCode} · {detail.market}
                </span>
              </h1>
              <p className={styles.subtitle}>
                고유번호: {detail.corpCode} | 업종코드: {detail.indutyCode || '-'} | CAPEX 추적:{' '}
                {detail.trackCapex ? '활성(추적 중)' : '비활성'}
              </p>
            </header>

            <section className={styles.section}>
              <h2 className={styles.sectionTitle}>1. 분기 실적</h2>
              {detail.latestQuarter ? (
                <div className={styles.summaryLine}>
                  <strong>
                    최근 분기 ({detail.latestQuarter.fiscalYear}Q{detail.latestQuarter.quarter},{' '}
                    {formatBasis(detail.latestQuarter.basis)})
                  </strong>
                  <span>매출액: {formatWon(detail.latestQuarter.revenue)}</span>
                  <span>영업이익: {formatWon(detail.latestQuarter.operatingProfit)}</span>
                  <span>당기순이익: {formatWon(detail.latestQuarter.netIncome)}</span>
                </div>
              ) : (
                <p className={styles.emptyText}>수집된 분기 실적 데이터가 없습니다.</p>
              )}

              {(detail.earningsTrend || []).length > 0 && (
                <div className={styles.tableWrap}>
                  <table className={styles.sheetTable}>
                    <thead>
                      <tr>
                        <th>분기</th>
                        <th>결산기준</th>
                        <th>구분</th>
                        <th className={styles.numCol}>매출액</th>
                        <th className={styles.numCol}>영업이익</th>
                        <th className={styles.numCol}>당기순이익</th>
                        <th>원문</th>
                      </tr>
                    </thead>
                    <tbody>
                      {[...detail.earningsTrend]
                        .sort((a, b) => (b.fiscalYear - a.fiscalYear) || (b.quarter - a.quarter))
                        .map((q) => (
                        <tr key={`${q.fiscalYear}-Q${q.quarter}`}>
                          <td>
                            {q.fiscalYear}년 {q.quarter}분기
                          </td>
                          <td>{q.periodEnd}</td>
                          <td>{formatBasis(q.basis)}</td>
                          <td className={styles.numCol}>{formatWon(q.revenue)}</td>
                          <td className={styles.numCol}>{formatWon(q.operatingProfit)}</td>
                          <td className={styles.numCol}>{formatWon(q.netIncome)}</td>
                          <td>
                            {q.sources?.[0]?.url ? (
                              <a
                                href={q.sources[0].url}
                                target="_blank"
                                rel="noopener noreferrer"
                                className={styles.link}
                              >
                                DART ↗
                              </a>
                            ) : (
                              '-'
                            )}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>

            <section className={styles.section}>
              <div className={styles.sectionHeader}>
                <h2 className={styles.sectionTitle}>2. 거래소 공시</h2>
                <div className={styles.filterGroup}>
                  {EVENT_FILTERS.map((f) => (
                    <button
                      key={f.id}
                      type="button"
                      className={`${styles.filterBtn} ${
                        eventFilter === f.id ? styles.filterBtnActive : ''
                      }`}
                      onClick={() => setEventFilter(f.id)}
                    >
                      {f.label}
                    </button>
                  ))}
                </div>
              </div>

              {eventFilter === 'CAPEX' && !detail.trackCapex && (
                <p className={styles.noticeText}>
                  현재 CAPEX 추적 대상 기업이 아닙니다. (삼성전자·SK하이닉스 기본 활성화, 추후 DB 설정으로 확장 가능)
                </p>
              )}

              {detailLoading ? (
                <div className={styles.statusText}>필터 적용 중...</div>
              ) : (detail.events || []).length === 0 ? (
                <p className={styles.emptyText}>해당 조건의 공시 기록이 없습니다.</p>
              ) : (
                <div className={styles.tableWrap}>
                  <table className={styles.sheetTable}>
                    <thead>
                      <tr>
                        <th>날짜</th>
                        <th>구분</th>
                        <th>요약 / 공시명</th>
                        <th>상세 수치</th>
                        <th>원문</th>
                      </tr>
                    </thead>
                    <tbody>
                      {detail.events.map((ev) => (
                        <tr key={ev.eventId}>
                          <td className={styles.nowrap}>{ev.publishedDate}</td>
                          <td className={styles.nowrap}>{ev.categoryLabel}</td>
                          <td>
                            <div className={styles.headlineCell}>{ev.headline}</div>
                            <div className={styles.reportSub}>{ev.reportTitle}</div>
                          </td>
                          <td>
                            {(ev.metrics || [])
                              .map((m) => `${m.label}: ${m.value}`)
                              .join(' / ') || '-'}
                          </td>
                          <td className={styles.nowrap}>
                            <a
                              href={ev.dartUrl}
                              target="_blank"
                              rel="noopener noreferrer"
                              className={styles.link}
                            >
                              DART ↗
                            </a>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </section>
          </>
        ) : null}
      </div>
    );
  }

  return (
    <div className={styles.container}>
      <header className={styles.listHeader}>
        <h1 className={styles.title}>공시 리서치 기업 목록</h1>
        <p className={styles.subtitle}>
          추적 대상 기업 ({totalCount}개) · 미확인 공시가 있는 기업에는 빨간 점이 표시됩니다.
        </p>
      </header>

      <div className={styles.searchRow}>
        <input
          type="text"
          className={styles.searchInput}
          placeholder="기업명 또는 종목코드 검색"
          value={searchInput}
          onChange={(e) => setSearchInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') setKeyword(searchInput.trim());
          }}
        />
        <button
          type="button"
          className={styles.searchBtn}
          onClick={() => setKeyword(searchInput.trim())}
        >
          검색
        </button>
        {keyword && (
          <button
            type="button"
            className={styles.resetBtn}
            onClick={() => {
              setSearchInput('');
              setKeyword('');
            }}
          >
            초기화
          </button>
        )}
      </div>

      {listLoading ? (
        <div className={styles.statusText}>기업 목록을 불러오는 중...</div>
      ) : listError ? (
        <div className={styles.statusText}>{listError}</div>
      ) : companies.length === 0 ? (
        <div className={styles.statusText}>수집된 기업 데이터가 없습니다.</div>
      ) : (
        <div className={styles.tableWrap}>
          <table className={styles.sheetTable}>
            <thead>
              <tr>
                <th>기업명</th>
                <th>종목코드</th>
                <th>시장</th>
                <th>최근 공시일</th>
                <th className={styles.numCol}>이벤트 건수</th>
                <th>CAPEX 추적</th>
              </tr>
            </thead>
            <tbody>
              {companies.map((c) => (
                <tr
                  key={c.corpCode}
                  className={styles.clickableRow}
                  onClick={() => handleSelectCompany(c.corpCode)}
                >
                  <td>
                    <span className={styles.companyNameCell}>
                      {c.hasUnread && (
                        <span
                          className={styles.unreadDot}
                          title="확인하지 않은 공시가 있습니다"
                          aria-label="미확인 공시"
                        />
                      )}
                      {c.companyName}
                    </span>
                  </td>
                  <td className={styles.nowrap}>{c.stockCode || c.corpCode}</td>
                  <td className={styles.nowrap}>{c.market}</td>
                  <td className={styles.nowrap}>{c.latestEventDate || '-'}</td>
                  <td className={styles.numCol}>{c.eventCount}</td>
                  <td className={styles.nowrap}>{c.trackCapex ? 'O' : '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
};

export default DisclosureBoard;
