import { Link, NavLink, useLocation } from 'react-router-dom';
import {
  Users,
  FileText,
  Calendar,
  Star,
  Gamepad2,
  BarChart3,
  Home,
  Activity,
  Server,
  Database,
  Settings,
  Shield,
  Upload,
} from 'lucide-react';
import styles from './AdminSidebar.module.css';

const presidentMenuItems = [
  {
    category: '회원',
    items: [
      { label: '회원 관리', href: '/admin/members', icon: Users },
      { label: '가입 승인', href: '/admin/members/approval', icon: Shield },
      { label: '엑셀 업로드', href: '/admin/members/upload', icon: Upload },
    ],
  },
  {
    category: '콘텐츠',
    items: [
      { label: '게시물 관리', href: '/admin/posts', icon: FileText },
      { label: '출석 관리', href: '/admin/attendance', icon: Calendar },
      { label: '포인트 관리', href: '/admin/points', icon: Star },
    ],
  },
  {
    category: '시스템',
    items: [
      { label: '게임/툴 관리', href: '/admin/tools', icon: Gamepad2 },
      { label: '통계 대시보드', href: '/admin/dashboard', icon: BarChart3 },
    ],
  },
];

const devMenuItems = [
  {
    category: '모니터링',
    items: [
      { label: '실시간 로그', href: '/admin/dev/logs', icon: Activity },
      { label: '시스템 리소스', href: '/admin/dev/resources', icon: Server },
      { label: 'API 통계', href: '/admin/dev/api', icon: BarChart3 },
      { label: 'DB 상태', href: '/admin/dev/database', icon: Database },
    ],
  },
];

const AdminSidebar = () => {
  const { pathname } = useLocation();
  const isDevSection = pathname?.startsWith('/admin/dev');

  return (
    <aside className={styles.sidebar}>
      <div className={styles.logoSection}>
        <Link to="/admin" className={styles.logoLink}>
          <div className={styles.logoMark}>
            <span className={styles.logoMarkText}>S</span>
          </div>
          <span className={styles.logoText}>세종투자연구회</span>
        </Link>
      </div>

      <div className={styles.tabSection}>
        <div className={styles.tabWrap}>
          <Link
            to="/admin"
            className={`${styles.tabButton} ${!isDevSection ? styles.tabActive : ''}`}
          >
            회장용
          </Link>
          <Link
            to="/admin/dev/logs"
            className={`${styles.tabButton} ${isDevSection ? styles.tabActive : ''}`}
          >
            개발자용
          </Link>
        </div>
      </div>

      <nav className={styles.nav}>
        {(isDevSection ? devMenuItems : presidentMenuItems).map((section) => (
          <div key={section.category} className={styles.section}>
            <div className={styles.sectionTitleWrap}>
              <span className={styles.sectionTitle}>
                {section.category}
              </span>
            </div>
            <ul className={styles.menuList}>
              {section.items.map((item) => {
                const isActive = pathname === item.href;
                return (
                  <li key={item.href}>
                    <NavLink
                      to={item.href}
                      className={`${styles.menuLink} ${isActive ? styles.menuLinkActive : ''}`}
                    >
                      <item.icon size={16} />
                      {item.label}
                    </NavLink>
                  </li>
                );
              })}
            </ul>
          </div>
        ))}
      </nav>

      <div className={styles.footer}>
        <Link
          to="/"
          className={styles.settingsLink}
        >
          <Home size={16} />
          홈페이지
        </Link>
        <Link
          to="/admin/settings"
          className={styles.settingsLink}
        >
          <Settings size={16} />
          설정
        </Link>
      </div>
    </aside>
  );
};

export default AdminSidebar;
