import { Outlet } from 'react-router-dom';
import Sidebar from './Sidebar';

function Layout() {
  return (
    <div style={{ display: 'flex' }}>
      <Sidebar />
      <main style={{ flex: 1, backgroundColor: '#ffffff' }}>
        <Outlet />
      </main>
    </div>
  );
}

export default Layout;
