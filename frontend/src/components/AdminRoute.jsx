import { useEffect, useState } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { api } from '../utils/axios';

const isAdminRole = (role) => {
  const normalizedRole = String(role || '').trim().toUpperCase();
  return (
    normalizedRole === 'PRESIDENT' ||
    normalizedRole === 'SYSTEM_ADMIN' ||
    normalizedRole === 'VICE_PRESIDENT'
  );
};

const AdminRoute = () => {
  const [loading, setLoading] = useState(true);
  const [isAuthorized, setIsAuthorized] = useState(false);
  const [redirectPath, setRedirectPath] = useState(null);
  const location = useLocation();

  useEffect(() => {
    const checkAdmin = async () => {
      try {
        const { data } = await api.get('/api/user/details');
        const role = data?.role;
        setIsAuthorized(isAdminRole(role));
      } catch (error) {
        setIsAuthorized(false);
        const returnUrl = encodeURIComponent(
          location.pathname + location.search
        );
        setRedirectPath(
          error?.status === 401 ? `/login?returnUrl=${returnUrl}` : '/'
        );
      } finally {
        setLoading(false);
      }
    };

    checkAdmin();
  }, [location.pathname, location.search]);

  if (loading) return <div>로딩 중...</div>;
  if (redirectPath) return <Navigate to={redirectPath} replace />;
  if (!isAuthorized) return <Navigate to="/" replace />;

  return <Outlet />;
};

export default AdminRoute;
