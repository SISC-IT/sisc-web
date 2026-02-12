import { createContext, useContext, useEffect, useState } from 'react';
import { api } from '../utils/axios';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [loading, setLoading] = useState(true);

  // 앱 시작 시 한 번만 로그인 체크
  useEffect(() => {
    const checkLogin = async () => {
      try {
        await api.get('/api/user/details');
        setIsLoggedIn(true);
      } catch (err) {
        setIsLoggedIn(false);
      } finally {
        setLoading(false);
      }
    };

    checkLogin();
  }, []);

  const login = () => setIsLoggedIn(true);

  const logout = async () => {
    try {
      await api.post('/api/auth/logout');
    } catch (e) {
      console.log('logout api 실패 무시');
    } finally {
      setIsLoggedIn(false);
      localStorage.removeItem('user');
    }
  };

  return (
    <AuthContext.Provider value={{ isLoggedIn, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => useContext(AuthContext);
