import { useState } from 'react';
import styles from './MyPageMenu.module.css';
import AttendanceIcon from '../../assets/s-coin-blue.svg';
import ActivityIcon from '../../assets/s-coin-purple.svg';
import PointPocketIcon from '../../assets/point-pocket.svg';
import { getMockByKey } from '../../utils/myPageMenuMock';
import ActivityModal from './ActivityModal';

const MENU_ITEMS = [
  {
    key: 'attendance',
    title: '출석관리',
    description: '세션별 출석을 확인할 수 있어요.',
    icon: AttendanceIcon,
    ariaLabel: '출석관리 열기',
  },
  {
    key: 'activity',
    title: '내 활동',
    description: '댓글, 작성한 글을 확인할 수 있어요.',
    icon: ActivityIcon,
    ariaLabel: '내 활동 열기',
  },
  {
    key: 'points',
    title: '포인트 내역',
    description: '포인트 기록을 확인할 수 있어요.',
    icon: PointPocketIcon,
    ariaLabel: '포인트 내역 열기',
  },
];

const MyPageMenu = () => {
  const [selectedKey, setSelectedKey] = useState(null);

  const selectedItem = MENU_ITEMS.find((m) => m.key === selectedKey);

  const data = getMockByKey(selectedKey);

  const handleOpen = (key) => setSelectedKey(key);
  const handleClose = () => setSelectedKey(null);

  return (
    <>
      <div className={styles.menuContainer}>
        {MENU_ITEMS.map((item) => (
          <div key={item.key} className={styles.menuItemWrapper}>
            <button
              type="button"
              className={styles.menuButton}
              aria-label={item.ariaLabel}
              onClick={() => handleOpen(item.key)}
            >
              <div className={styles.menuTextBox}>
                <h3 className={styles.menuTitle}>
                  {item.title}
                  <span className={styles.chevronRight}>&gt;</span>
                </h3>
              </div>
              <p className={styles.menuDesc}>{item.description}</p>
              <img
                src={item.icon}
                alt=""
                aria-hidden="true"
                className={styles.menuIcon}
              />
            </button>
          </div>
        ))}
      </div>

      <ActivityModal
        isOpen={!!selectedItem}
        onClose={handleClose}
        title={selectedItem?.title || ''}
        kind={selectedItem?.key}
        data={data}
      />
    </>
  );
};

export default MyPageMenu;
