import React, { useState, useEffect, useCallback } from 'react';
import CategoryTabs from '../components/Board/CategoryTabs';
import SearchArrowIcon from '../assets/boardSearchArrow.svg';
import { getDisclosureCards } from '../utils/disclosureCardApi';
import styles from './DisclosureCardBoard.module.css';

const CATEGORY_TABS = [
  { id: 'ALL', name: '전체' },
  { id: 'PRELIMINARY_EARNINGS', name: '잠정 실적' },
  { id: 'CONFIRMED_EARNINGS', name: '확정 실적' },
  { id: 'SHAREHOLDER_RETURN', name: '주주환원' },
  { id: 'SEMICONDUCTOR_CAPEX', name: '반도체 CAPEX' },
];

const BADGE_CLASS_MAP = {
  PRELIMINARY_EARNINGS: styles.badgePrelim,
  CONFIRMED_EARNINGS: styles.badgeConfirmed,
  SHAREHOLDER_RETURN: styles.badgeShareholder,
  SEMICONDUCTOR_CAPEX: styles.badgeCapex,
};

const RULE_HINTS = {
  ALL: ['영업이익 YoY ±20%↑ / 흑·적자전환', '자사주 소각·100억↑ / 배당률 3.5%↑', '반도체 업종 CAPEX 5%↑'],
  PRELIMINARY_EARNINGS: ['영업이익 YoY ±20% 이상', '흑자전환 · 적자전환 포함', '기재정정 · 자율공시 제외'],
  CONFIRMED_EARNINGS: ['정기보고서 확정 실적', '연결(CFS) 우선 반영', '최근 분기 요약'],
  SHAREHOLDER_RETURN: ['자사주 소각 전체', '자사주 취득 100억↑', '시가배당률 3.5%↑ / 배당총액 500억↑'],
  SEMICONDUCTOR_CAPEX: ['반도체 제조(261) · 장비(29271)', '자기자본대비 5%↑ 또는 300억↑'],
};

const DisclosureCardBoard = () => {
  const [activeCategory, setActiveCategory] = useState('ALL');
  const [searchInput, setSearchInput] = useState('');
  const [queryKeyword, setQueryKeyword] = useState('');
  const [sortOption, setSortOption] = useState('latest');
  const [currentPage, setCurrentPage] = useState(0);
  const [pageData, setPageData] = useState({
    items: [],
    totalCount: 0,
    page: 0,
    size: 12,
    totalPages: 0,
  });
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState('');
  const [selectedCard, setSelectedCard] = useState(null);

  const fetchCards = useCallback(async () => {
    setLoading(true);
    setErrorMessage('');
    try {
      const response = await getDisclosureCards({
        category: activeCategory,
        q: queryKeyword,
        sort: sortOption,
        page: currentPage,
        size: 12,
      });
      setPageData({
        items: response?.items || [],
        totalCount: response?.totalCount || 0,
        page: response?.page || 0,
        size: response?.size || 12,
        totalPages: response?.totalPages || 0,
      });
    } catch {
      setErrorMessage('공시 카드 뉴스를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.');
      setPageData((prev) => ({ ...prev, items: [], totalCount: 0, totalPages: 0 }));
    } finally {
      setLoading(false);
    }
  }, [activeCategory, queryKeyword, sortOption, currentPage]);

  useEffect(() => {
    fetchCards();
  }, [fetchCards]);

  const handleSearch = () => {
    setCurrentPage(0);
    setQueryKeyword(searchInput.trim());
  };

  const handleCategoryChange = (categoryId) => {
    setActiveCategory(categoryId);
    setCurrentPage(0);
  };

  const handleSortChange = (event) => {
    setSortOption(event.target.value);
    setCurrentPage(0);
  };

  const ruleHints = RULE_HINTS[activeCategory] || RULE_HINTS.ALL;

  return (
    <div className={styles.boardContainer}>
      <div className={styles.boardHeader}>
        <h1 className={styles.boardTitle}>공시 카드 뉴스</h1>
        <p className={styles.boardSubtitle}>
          OpenDART 잠정·확정 실적, 주주환원, 반도체 업종 CAPEX 공시를 규칙 기반으로 자동 요약합니다.
        </p>
      </div>

      <div className={styles.boardControls}>
        <div className={styles.searchContainer}>
          <input
            type="text"
            className={styles.searchInput}
            placeholder="종목명, 종목코드 또는 공시 키워드를 입력하세요"
            value={searchInput}
            onChange={(e) => setSearchInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleSearch();
            }}
          />
          <button
            type="button"
            className={styles.searchButton}
            onClick={handleSearch}
            aria-label="공시 검색"
          >
            <img src={SearchArrowIcon} alt="검색" />
          </button>
        </div>

        <CategoryTabs
          activeTab={activeCategory}
          onTabChange={handleCategoryChange}
          tabs={CATEGORY_TABS}
        />
      </div>

      <div className={styles.toolbarRow}>
        <div className={styles.toolbarLeft}>
          <span className={styles.resultCount}>
            {pageData.totalCount.toLocaleString()}건의 공시 카드
          </span>
          <span className={styles.toolbarDivider} />
          <div className={styles.ruleChipGroup}>
            {ruleHints.map((hint) => (
              <span key={hint} className={styles.ruleChip}>
                {hint}
              </span>
            ))}
          </div>
        </div>

        <div className={styles.toolbarRight}>
          <select
            className={styles.sortSelect}
            value={sortOption}
            onChange={handleSortChange}
            aria-label="정렬 기준"
          >
            <option value="latest">최신순</option>
            <option value="oldest">오래된순</option>
          </select>
        </div>
      </div>

      {loading ? (
        <div className={styles.stateBox}>공시 카드 뉴스를 불러오는 중입니다...</div>
      ) : errorMessage ? (
        <div className={styles.stateBox}>{errorMessage}</div>
      ) : pageData.items.length === 0 ? (
        <div className={styles.stateBox}>
          조건에 맞는 공시 카드 뉴스가 없습니다.
        </div>
      ) : (
        <div className={styles.cardGrid}>
          {pageData.items.map((card) => (
            <article
              key={card.cardId}
              className={styles.card}
              onClick={() => setSelectedCard(card)}
            >
              <div className={styles.cardMetaRow}>
                <div className={styles.badgeGroup}>
                  <span
                    className={`${styles.categoryBadge} ${BADGE_CLASS_MAP[card.category] || ''}`}
                  >
                    {card.categoryLabel}
                  </span>
                  <span className={styles.marketBadge}>{card.market}</span>
                </div>
                <span className={styles.cardDate}>{card.publishedDate}</span>
              </div>

              <div className={styles.companyLine}>
                <span>{card.companyName}</span>
                {card.stockCode && (
                  <span className={styles.stockCode}>{card.stockCode}</span>
                )}
              </div>

              <h2 className={styles.cardHeadline}>{card.headline}</h2>

              <div className={styles.metricStrip}>
                {(card.metrics || []).slice(0, 3).map((metric, idx) => (
                  <div key={`${card.cardId}-m-${idx}`} className={styles.metricCell}>
                    <span className={styles.metricLabel}>{metric.label}</span>
                    <span
                      className={`${styles.metricValue} ${metric.highlight ? styles.metricHighlight : ''}`}
                    >
                      {metric.value}
                    </span>
                  </div>
                ))}
              </div>

              <div className={styles.cardFooter}>
                <a
                  href={card.dartUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className={styles.dartLink}
                  onClick={(e) => e.stopPropagation()}
                >
                  DART 원문 ↗
                </a>
                <div className={styles.tagRow}>
                  {(card.tags || []).slice(0, 2).map((tag) => (
                    <span key={tag} className={styles.tagItem}>
                      #{tag}
                    </span>
                  ))}
                </div>
              </div>
            </article>
          ))}
        </div>
      )}

      {pageData.totalPages > 1 && (
        <div className={styles.pagination}>
          <button
            type="button"
            className={styles.pageNavButton}
            disabled={currentPage <= 0}
            onClick={() => setCurrentPage((prev) => Math.max(prev - 1, 0))}
          >
            이전
          </button>
          {Array.from({ length: pageData.totalPages }, (_, idx) => idx).map((pageNum) => (
            <button
              key={pageNum}
              type="button"
              className={`${styles.pageNumButton} ${currentPage === pageNum ? styles.pageNumActive : ''}`}
              onClick={() => setCurrentPage(pageNum)}
            >
              {pageNum + 1}
            </button>
          ))}
          <button
            type="button"
            className={styles.pageNavButton}
            disabled={currentPage >= pageData.totalPages - 1}
            onClick={() =>
              setCurrentPage((prev) => Math.min(prev + 1, pageData.totalPages - 1))
            }
          >
            다음
          </button>
        </div>
      )}

      {selectedCard && (
        <div className={styles.modalOverlay} onClick={() => setSelectedCard(null)}>
          <div className={styles.modalContent} onClick={(e) => e.stopPropagation()}>
            <div className={styles.modalHeader}>
              <div className={styles.badgeGroup}>
                <span
                  className={`${styles.categoryBadge} ${BADGE_CLASS_MAP[selectedCard.category] || ''}`}
                >
                  {selectedCard.categoryLabel}
                </span>
                <span className={styles.marketBadge}>{selectedCard.market}</span>
              </div>
              <button
                type="button"
                className={styles.modalClose}
                onClick={() => setSelectedCard(null)}
                aria-label="닫기"
              >
                ×
              </button>
            </div>

            <div className={styles.companyLine}>
              <span>{selectedCard.companyName}</span>
              {selectedCard.stockCode && (
                <span className={styles.stockCode}>{selectedCard.stockCode}</span>
              )}
              <span className={styles.cardDate}>· {selectedCard.publishedDate}</span>
            </div>

            <h2 className={styles.modalTitle}>{selectedCard.headline}</h2>
            <p className={styles.modalReportTitle}>공시명: {selectedCard.reportTitle}</p>

            <div className={styles.metricStrip}>
              {(selectedCard.metrics || []).map((metric, idx) => (
                <div key={`modal-m-${idx}`} className={styles.metricCell}>
                  <span className={styles.metricLabel}>{metric.label}</span>
                  <span
                    className={`${styles.metricValue} ${metric.highlight ? styles.metricHighlight : ''}`}
                  >
                    {metric.value}
                  </span>
                </div>
              ))}
            </div>

            <div className={styles.modalFooter}>
              <span className={styles.receiptLabel}>접수번호: {selectedCard.receiptNo}</span>
              <a
                href={selectedCard.dartUrl}
                target="_blank"
                rel="noopener noreferrer"
                className={styles.modalDartButton}
              >
                DART 공시 원문 보기 ↗
              </a>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default DisclosureCardBoard;
