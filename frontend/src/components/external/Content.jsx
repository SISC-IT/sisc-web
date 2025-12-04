import styles from './Content.module.css';
import { pages } from '../../utils/reportContent';
import { useState } from 'react';

const Content = () => {
  const [current, setCurrent] = useState(0);
  const goPrev = () => {
    if (current > 1) setCurrent(current - 2);
  };

  const goNext = () => {
    if (current < pages.length - 2) setCurrent(current + 2);
  };
  return (
    <div className={styles.content}>
      <div className={styles.progressWrapper}>
        <div
          className={styles.progressBar}
          style={{
            width: `${((current + 2) / pages.length) * 100}%`,
          }}
        />
      </div>

      <div className={styles.container}>
        {/* 좌측 화살표 */}
        <button
          className={styles.arrowBtn}
          onClick={goPrev}
          disabled={current === 0}
        >
          <span className={styles.leftArrowIcon}></span>
        </button>

        {/* 이미지 페이지 */}
        <div className={styles.pageSection}>
          <img src={pages[current]} className={styles.page} alt="report" />
          <img src={pages[current + 1]} className={styles.page} alt="report" />
        </div>

        {/* 우측 화살표 */}
        <button
          className={styles.arrowBtn}
          onClick={goNext}
          disabled={current >= pages.length - 2}
        >
          <span className={styles.rightArrowIcon}></span>
        </button>
      </div>
    </div>
  );
};

export default Content;
