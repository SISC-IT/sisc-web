import { Outlet, useLocation } from 'react-router-dom';
import Header from './Header';
import Sidebar from './Sidebar';
import { useState } from 'react';

function Layout() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const location = useLocation();
  const isRoot = location.pathname === '/';

  return (
    <div style={{ display: 'flex', position: 'relative', minHeight: '100vh' }}>
      <Header
        isRoot={isRoot}
        onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
        style={{ position: 'fixed', top: 0, left: 0, right: 0 }}
      />

      <main
        style={{
          display: 'flex',
          flex: 1,
          marginLeft: !isRoot ? '264px' : '0',
          transition: 'margin-left 0.3s ease',
        }}
      >
        <Sidebar isOpen={isSidebarOpen} isRoot={isRoot} />
        <div style={{ flex: 1 }}>
          <Outlet />
        </div>
      </main>
    </div>
  );
}

export default Layout;
