import { useState, useEffect } from 'react';
import styles from './ActivityModal.module.css';
import { getActivityLogs } from '../../utils/myPageMenu';

const ActivitySection = () => {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const size = 5;

  const fetchActivityLogs = async (page) => {
    try {
      const res = await getActivityLogs(page, size);

      setItems(
        (res?.content || []).map((it) => ({
          id: it.id,
          content: it.message,
          time: new Date(it.createdAt).toLocaleString(),
        }))
      );

      setTotalPages(res?.totalPages || 1);
    } catch (error) {
      console.error(error);
    }
  };

  useEffect(() => {
    fetchActivityLogs(page);
  }, [page]);

  const renderPagination = () => {
    const pages = [];

    for (let i = 0; i < totalPages; i++) {
      pages.push(
        <button
          key={i}
          className={`${styles.pageButton} ${i === page ? styles.activePage : ''}`}
          onClick={() => setPage(i)}
        >
          {i + 1}
        </button>
      );
    }

    return <div className={styles.pagination}>{pages}</div>;
  };

  return (
    <div>
      <ul className={styles.list}>
        {items.map((it) => (
          <li key={it.id} className={styles.row}>
            <div className={styles.left}>
              <div className={styles.title}>{it.content}</div>
              <div className={styles.time}>{it.time}</div>
            </div>
          </li>
        ))}
      </ul>

      {renderPagination()}
    </div>
  );
};

export default ActivitySection;
