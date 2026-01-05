import { Outlet, useLocation } from 'react-router-dom';
import Header from './Header';
import Sidebar from './Sidebar';
import { useState, useEffect } from 'react';

function Layout() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const [isMobile, setIsMobile] = useState(false);
  const location = useLocation();
  const isRoot = location.pathname === '/';

  useEffect(() => {
    const handleResize = () => {
      setIsMobile(window.innerWidth < 1024);
      // 데스크톱에서는 사이드바 자동 닫기
      if (window.innerWidth >= 1024) {
        setIsSidebarOpen(false);
      }
    };

    handleResize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  // 페이지 변경 시 모바일에서 사이드바 닫기
  useEffect(() => {
    if (isMobile) {
      setIsSidebarOpen(false);
    }
  }, [location.pathname, isMobile]);

  // 모바일에서 사이드바가 열려있을 때 body 스크롤 방지
  useEffect(() => {
    if (isMobile && isSidebarOpen) {
      document.body.style.overflow = 'hidden';
    } else {
      document.body.style.overflow = 'unset';
    }

    return () => {
      document.body.style.overflow = 'unset';
    };
  }, [isMobile, isSidebarOpen]);

  return (
    <div style={{ display: 'flex', position: 'relative', minHeight: '100vh' }}>
      <Header
        isRoot={isRoot}
        onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
        isOpen={isSidebarOpen}
        style={{ position: 'fixed' }}
      />

      {/* 사이드바 - 레이아웃 밖에서 독립적으로 렌더링 */}
      <Sidebar 
        isOpen={isSidebarOpen} 
        isRoot={isRoot} 
        onClose={() => setIsSidebarOpen(false)} 
      />

      <main
        style={{
          display: 'flex',
          flex: 1,
          marginLeft: !isRoot && !isMobile ? '264px' : '0',
          marginTop: isRoot ? '0' : '90px',
          transition: 'margin-left 0.3s ease',
          width: '100%',
        }}
      >
        <div style={{ flex: 1, width: '100%', overflow: 'hidden' }}>
          <Outlet />
        </div>
      </main>
    </div>
  );
}

export default Layout;
