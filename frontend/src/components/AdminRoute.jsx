import { useEffect, useState } from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { api } from '../utils/axios';

const isAdminRole = (role) => {
  const normalizedRole = String(role || '').trim().toUpperCase();
  return normalizedRole === 'PRESIDENT' || normalizedRole === 'SYSTEM_ADMIN';
};

const AdminRoute = () => {
  const [loading, setLoading] = useState(true);
  const [isAuthorized, setIsAuthorized] = useState(false);
  const [redirectPath, setRedirectPath] = useState(null);

  useEffect(() => {
    const checkAdmin = async () => {
      try {
        const { data } = await api.get('/api/user/details');
        setIsAuthorized(isAdminRole(data?.role));
      } catch (error) {
        setIsAuthorized(false);
        setRedirectPath(error?.status === 401 ? '/login' : '/');
      } finally {
        setLoading(false);
      }
    };

    checkAdmin();
  }, []);

  if (loading) return <div>로딩 중...</div>;
  if (redirectPath) return <Navigate to={redirectPath} replace />;
  if (!isAuthorized) return <Navigate to="/" replace />;

  return <Outlet />;
};

export default AdminRoute;
