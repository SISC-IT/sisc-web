import './App.css';
import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Home from './pages/Home';
import Attendance from './pages/Attendance';
import Board from './pages/Board';
import PostDetail from './pages/PostDetail';
import StockGame from './pages/StockGame';
import BackTest from './pages/BackTest';
import Mypage from './pages/Mypage';
import AttendanceManage from './pages/AttendanceManage';
import Login from './pages/Login';
import SignUp from './pages/SignUp';
import QuantTradingDashboard from './pages/QuantTradingDashboard';
import BacktestResult from './pages/BacktestResult.jsx';

import OAuthSuccess from './pages/OAuthSuccess.jsx';

import Main from './pages/external/Main.jsx';
import Intro from './pages/external/Intro.jsx';
import Leaders from './pages/external/Leaders.jsx';
import Portfolio from './pages/external/Portfolio.jsx';

import { ToastContainer } from 'react-toastify';
import 'react-toastify/dist/ReactToastify.css';

function App() {
  return (
    <>
      <Routes>
        <Route path="/main" element={<Main />} />
        <Route path="/main/intro" element={<Intro />} />
        <Route path="/main/leaders" element={<Leaders />} />
        <Route path="/main/portfolio" element={<Portfolio />} />
        <Route path="/login" element={<Login />} />
        <Route path="/signup" element={<SignUp />} />
        <Route path="/oauth/success" element={<OAuthSuccess />} />
        <Route element={<Layout />}>
          <Route path="/" element={<Home />} />
          <Route path="/attendance" element={<Attendance />} />
          <Route path="/attendance-manage" element={<AttendanceManage />} />
          <Route path="/board" element={<Board />} />
          <Route path="/board/:team" element={<Board />} />
          <Route path="/board/:team/:postId" element={<PostDetail />} />
          <Route path="/quant-bot" element={<QuantTradingDashboard />} />
          <Route path="/stock-game" element={<StockGame />} />
          <Route path="/backtest" element={<BackTest />} />
          <Route path="/backtest/result" element={<BacktestResult />} />
          <Route path="/mypage" element={<Mypage />} />
        </Route>
      </Routes>
      <ToastContainer
        position="top-center"
        autoClose={2000}
        hideProgressBar={false}
        pauseOnHover
        theme="light"
      />
    </>
  );
}

export default App;
