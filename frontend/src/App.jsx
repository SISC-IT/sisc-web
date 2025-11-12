import './App.css';
import { Routes, Route, useLocation, useNavigate } from 'react-router-dom';
import { useEffect } from 'react';
import Layout from './components/Layout';
import Home from './pages/Home';
import Attendance from './pages/Attendance';
import Board from './pages/Board';
import StockGame from './pages/StockGame';
import BackTest from './pages/BackTest';
import Mypage from './pages/Mypage';
import AttendanceManage from './pages/AttendanceManage';
import Login from './pages/Login';
import SignUp from './pages/SignUp';
import QuantBot from './pages/QuantBot';

function App() {
  const location = useLocation();
  const navigate = useNavigate();

  // OAuth 콜백 처리: URL 파라미터에서 accessToken, userId, name 추출 및 저장
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const accessToken = params.get('accessToken');
    const userId = params.get('userId');
    const name = params.get('name');

    if (accessToken && userId) {
      localStorage.setItem('accessToken', accessToken);
      localStorage.setItem('userId', userId);
      if (name) {
        localStorage.setItem('userName', name);
      }
      console.log('✅ OAuth 로그인 완료:', { userId, name, tokenLength: accessToken.length });

      // 쿼리 파라미터 제거하고 홈으로 이동
      navigate('/', { replace: true });
    }
  }, [location.search, navigate]);

  return (
    <>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<SignUp />} />
        <Route element={<Layout />}>
          <Route path="/" element={<Home />} />
          <Route path="/attendance" element={<Attendance />} />
          <Route path="/attendance-manage" element={<AttendanceManage />} />
          <Route path="/board" element={<Board />} />
          <Route path="/quant-bot" element={<QuantBot />} />
          <Route path="/stock-game" element={<StockGame />} />
          <Route path="/back-test" element={<BackTest />} />
          <Route path="/mypage" element={<Mypage />} />
        </Route>
      </Routes>
    </>
  );
}

export default App;
