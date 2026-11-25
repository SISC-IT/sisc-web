import { useCallback, useEffect, useState } from 'react';

// 서버의 Page 응답(content, last)을 '더 보기'로 이어 붙인다. reload()는 첫 페이지부터 다시 읽는다.
const usePagedList = (fetchPage) => {
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
        setItems((prev) =>
          page === 0 ? res.content : [...(prev ?? []), ...res.content]
        );
        setLast(res.last);
        setError('');
      })
      .catch((err) => !stale && setError(err.message))
      .finally(() => !stale && setLoading(false));
    return () => {
      stale = true;
    };
  }, [fetchPage, page, reloadCount]);

  const loadMore = () => setPage((p) => p + 1);
  const reload = useCallback(() => {
    setPage(0);
    setReloadCount((c) => c + 1);
  }, []);

  return { items, last, error, loading, loadMore, reload };
};

export default usePagedList;
