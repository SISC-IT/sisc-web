import { useCallback, useEffect, useMemo, useState } from 'react';
import { MessageSquare, RefreshCw } from 'lucide-react';
import styles from './AdminFeedbackList.module.css';
import { getAdminFeedbacks } from '../../utils/adminFeedbackApi';

const PAGE_SIZE = 10;

const formatDateTime = (value) => {
  if (!value) return '-';

  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '-';

  return date.toLocaleString('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
};

const AdminFeedbackList = () => {
  const [feedbacks, setFeedbacks] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState('');

  const fetchFeedbacks = useCallback(async (nextPage = page) => {
    setIsLoading(true);
    setError('');

    try {
      const result = await getAdminFeedbacks({ page: nextPage, size: PAGE_SIZE });
      const content = Array.isArray(result?.content) ? result.content : [];

      setFeedbacks(content);
      setPage(result?.number ?? nextPage);
      setTotalPages(result?.totalPages ?? 0);
      setTotalElements(result?.totalElements ?? content.length);
    } catch (loadError) {
      setFeedbacks([]);
      setError(loadError?.message || '피드백을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.');
    } finally {
      setIsLoading(false);
    }
  }, [page]);

  useEffect(() => {
    fetchFeedbacks(0);
  }, [fetchFeedbacks]);

  const pageLabel = useMemo(() => {
    if (totalPages === 0) return '0 / 0';
    return `${page + 1} / ${totalPages}`;
  }, [page, totalPages]);

  const goPrev = () => {
    if (isLoading || page <= 0) return;
    fetchFeedbacks(page - 1);
  };

  const goNext = () => {
    if (isLoading || page + 1 >= totalPages) return;
    fetchFeedbacks(page + 1);
  };

  return (
    <section className={styles.container}>
      <div className={styles.headerRow}>
        <div className={styles.titleWrap}>
          <MessageSquare size={18} />
          <h2 className={styles.title}>사용자 피드백</h2>
          <span className={styles.count}>총 {totalElements.toLocaleString()}건</span>
        </div>

        <button
          type="button"
          className={styles.refreshButton}
          onClick={() => fetchFeedbacks(page)}
          disabled={isLoading}
        >
          <RefreshCw size={16} className={isLoading ? styles.spin : ''} />
          새로고침
        </button>
      </div>

      {error && <p className={styles.errorText}>{error}</p>}

      <div className={styles.listWrap}>
        {isLoading && <p className={styles.statusText}>피드백을 불러오는 중입니다...</p>}

        {!isLoading && feedbacks.length === 0 && !error && (
          <p className={styles.statusText}>등록된 피드백이 없습니다.</p>
        )}

        {!isLoading && feedbacks.length > 0 && (
          <ul className={styles.list}>
            {feedbacks.map((item) => (
              <li key={item.feedbackId} className={styles.item}>
                <div className={styles.itemMeta}>
                  <span className={styles.feedbackId}>익명</span>
                  <time className={styles.dateText}>{formatDateTime(item.createdDate)}</time>
                </div>
                <p className={styles.content}>{item.content || '-'}</p>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className={styles.pagination}>
        <button
          type="button"
          className={styles.pageButton}
          onClick={goPrev}
          disabled={isLoading || page <= 0}
        >
          이전
        </button>
        <span className={styles.pageLabel}>{pageLabel}</span>
        <button
          type="button"
          className={styles.pageButton}
          onClick={goNext}
          disabled={isLoading || page + 1 >= totalPages}
        >
          다음
        </button>
      </div>
    </section>
  );
};

export default AdminFeedbackList;
