import { useState, useEffect } from 'react';
import styles from './ActivityModal.module.css';
import PointIcon from '../../assets/coin4.svg';
import { getUserPoints } from '../../utils/myPageMenu';

const PointsSection = () => {
  const [items, setItems] = useState([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const pageSize = 10;

  const fetchPoints = async (pageNumber) => {
    try {
      const res = await getUserPoints(pageNumber, pageSize);
      setItems(
        res.content.map((it) => ({
          id: it.entryId,
          content: it.reason,
          point: it.amount,
          time: new Date(it.createdDate).toLocaleString(),
        }))
      );
      setTotalPages(res.totalPages);
    } catch (error) {
      console.error(error);
    }
  };

  useEffect(() => {
    fetchPoints(page);
  }, [page]);

  const renderPagination = () => {
    const pages = [];
    for (let i = 1; i <= totalPages; i++) {
      pages.push(
        <button
          key={i}
          className={`${styles.pageButton} ${i === page ? styles.activePage : ''}`}
          onClick={() => setPage(i)}
        >
          {i}
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
            <div className={styles.right}>
              <img
                src={PointIcon}
                alt=""
                aria-hidden="true"
                className={styles.pointIcon}
              />
              <span
                className={it.point >= 0 ? styles.pointPlus : styles.pointMinus}
              >
                {it.point >= 0 ? `+${it.point}` : it.point}P
              </span>
            </div>
          </li>
        ))}
      </ul>

      {renderPagination()}
    </div>
  );
};
export default PointsSection;
