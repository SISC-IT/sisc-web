import styles from './Pagination.module.css';

const Pagination = ({ totalPages, currentPage, onPageChange }) => {
  const handleClick = (page) => {
    if (page < 1 || page > totalPages) return;
    onPageChange(page);
  };

  const getPages = () => {
    const pages = [];
    const maxVisible = 5;
    let start = Math.max(1, currentPage - Math.floor(maxVisible / 2));
    const end = Math.min(totalPages, start + maxVisible - 1);

    if (end - start < maxVisible - 1) {
      start = Math.max(1, end - maxVisible + 1);
    }

    for (let i = start; i <= end; i++) pages.push(i);
    return pages;
  };

  const pages = getPages();

  return (
    <div className={styles.pagination}>
      <button
        className={styles.arrow}
        onClick={() => handleClick(currentPage - 1)}
        disabled={currentPage === 1}
      >
        &lt; 이전
      </button>

      {pages[0] > 1 && (
        <>
          <button className={styles.page} onClick={() => handleClick(1)}>
            1
          </button>
          {totalPages > 10 && <span className={styles.ellipsis}>...</span>}
        </>
      )}

      {pages.map((page) => (
        <button
          key={page}
          className={`${styles.page} ${
            page === currentPage ? styles.active : ''
          }`}
          onClick={() => handleClick(page)}
        >
          {page}
        </button>
      ))}

      {pages[pages.length - 1] < totalPages && (
        <>
          {totalPages > 10 && <span className={styles.ellipsis}>...</span>}
          <button
            className={styles.page}
            onClick={() => handleClick(totalPages)}
          >
            {totalPages}
          </button>
        </>
      )}

      <button
        className={styles.arrow}
        onClick={() => handleClick(currentPage + 1)}
        disabled={currentPage === totalPages}
      >
        다음 &gt;
      </button>
    </div>
  );
};

export default Pagination;
