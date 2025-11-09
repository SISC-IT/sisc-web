import { Outlet, useLocation } from 'react-router-dom';
import Sidebar from './Sidebar';

function Layout() {
  const location = useLocation();
  const isRoot = location.pathname === '/';

  return (
    <div style={{ display: 'flex', position: 'relative' }}>
      {/* 홈이 아닐 때는 Sidebar 고정 */}
      {!isRoot && <Sidebar />}

      <main
        style={{ flex: 1, backgroundColor: '#ffffff', position: 'relative' }}
      >
        {/* 홈일 때만 Sidebar를 main 내부에서 렌더링 (햄버거 + 슬라이드) */}
        {isRoot && <Sidebar />}
        <Outlet />
      </main>
    </div>
  );
}

export default Layout;
