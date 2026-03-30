import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

const ProtectedRoute = () => {
  const { isLoggedIn, loading } = useAuth();
  const location = useLocation();

  if (loading) return <div>로딩 중...</div>;
  if (!isLoggedIn) {
    const returnUrl = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?returnUrl=${returnUrl}`} replace />;
  }

  return <Outlet />;
};

export default ProtectedRoute;
