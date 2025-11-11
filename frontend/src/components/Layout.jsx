import { Outlet, useLocation } from 'react-router-dom';
import Header from './Header';
import Sidebar from './Sidebar';
import { useState } from 'react';

function Layout() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const location = useLocation();
  const isRoot = location.pathname === '/';

  return (
    <div style={{ display: 'flex', position: 'relative' }}>
      <Header
        onToggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
        style={{ position: 'fixed' }}
      />
      <main
        style={{
          display: 'flex',
          position: 'relative',
          marginLeft: !isRoot ? '264px' : '0',
          transition: 'margin-left 0.3s ease',
        }}
      >
        <Sidebar isOpen={isSidebarOpen} isRoot={isRoot} />
        <Outlet />
      </main>
    </div>
  );
}

export default Layout;
