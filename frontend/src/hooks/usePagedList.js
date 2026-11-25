import { useCallback, useEffect, useState } from 'react';

// 서버의 Page 응답(content, last)을 '더 보기'로 이어 붙인다. reload()는 첫 페이지부터 다시 읽는다.
const usePagedList = (fetchPage, idKey) => {
  const [items, setItems] = useState(null);
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [reloadCount, setReloadCount] = useState(0);

  useEffect(() => {
    let stale = false;
    setLoading(true);
    fetchPage(page)
      .then((res) => {
        if (stale) return;
        setItems((prev) => {
          if (page === 0) return res.content;
          // 페이지 사이에 새 기록이 생기면 같은 행이 다음 페이지에 또 온다
          const seen = new Set(prev.map((item) => item[idKey]));
          return [
            ...prev,
            ...res.content.filter((item) => !seen.has(item[idKey])),
          ];
        });
        setLast(res.last);
        setError('');
      })
      .catch((err) => !stale && setError(err.message))
      .finally(() => !stale && setLoading(false));
    return () => {
      stale = true;
    };
  }, [fetchPage, idKey, page, reloadCount]);

  // 실패한 페이지는 건너뛰지 않고 다시 읽는다
  const loadMore = () =>
    error ? setReloadCount((c) => c + 1) : setPage((p) => p + 1);
  const reload = useCallback(() => {
    setPage(0);
    setReloadCount((c) => c + 1);
  }, []);

  return { items, last, error, loading, loadMore, reload };
};

export default usePagedList;
