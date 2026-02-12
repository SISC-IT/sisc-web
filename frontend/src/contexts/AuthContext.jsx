import { createContext, useContext, useEffect, useState } from 'react';
import { api } from '../utils/axios';

const AuthContext = createContext(null);

export const AuthProvider = ({ children }) => {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    const checkLogin = async () => {
      try {
        await api.get('/api/user/details');
        setIsLoggedIn(true);
      } catch {
        setIsLoggedIn(false);
      } finally {
        setLoading(false);
      }
    };

    checkLogin();
  }, []);

  const login = async ({ studentId, password }, signal) => {
    const paylaod = { studentId, password };

    const res = await api.post('/api/auth/login', paylaod, { signal });
    // 토큰은 자동으로 쿠키에 저장됨 (백엔드가 Set-Cookie 헤더로 전송)
    // res.data에는 유저 정보만 있음 (accessToken, refreshToken 제거됨)

    return res.data;
  };

  const logout = async () => {
    try {
      await api.post('/api/auth/logout');
    } catch (error) {
      // 로그아웃 API 실패해도 무시 (토큰이 없을 수 있음)
      console.log('로그아웃 API 호출 실패:', error.message);
    } finally {
      // localStorage 유저 정보 삭제
      localStorage.removeItem('user');
    }
  };

  return (
    <AuthContext.Provider value={{ isLoggedIn, loading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
