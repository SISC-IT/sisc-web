import { Bell, Search, User } from 'lucide-react';
import styles from './AdminHeader.module.css';

const AdminHeader = ({ title }) => {
  return (
    <header className={styles.header}>
      <div className={styles.inner}>
        <div className={styles.titleWrap}>
          {title && <h1 className={styles.title}>{title}</h1>}
        </div>

        <div className={styles.actions}>
          <div className={styles.searchWrap}>
            <Search size={16} className={styles.searchIcon} />
            <input
              type="search"
              placeholder="검색..."
              className={styles.searchInput}
            />
          </div>

          <button type="button" className={styles.iconButton} aria-label="알림">
            <Bell size={16} />
            <span className={styles.dot} />
          </button>

          <button type="button" className={styles.userButton} aria-label="관리자 계정">
            <User size={16} />
          </button>
        </div>
      </div>
    </header>
  );
};

export default AdminHeader;
