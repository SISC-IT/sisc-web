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
import AttendanceAll from './pages/AttendanceAll';
import AttendanceAsset from './pages/AttendanceAsset';
import AttendanceFinanceIt from './pages/AttendanceFinanceIt';
import AttendanceManage from './pages/AttendanceManage';
import QuantBot from './pages/QuantBot';

function App() {
  return (
    <>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Home />} />
          <Route path="/attendance" element={<Attendance />} />
          <Route path="/attendance-all" element={<AttendanceAll />} />
          <Route path="/attendance-asset" element={<AttendanceAsset />} />
          <Route
            path="/attendance-financeit"
            element={<AttendanceFinanceIt />}
          />
          <Route path="/attendance-manage" element={<AttendanceManage />} />
          <Route path="/board" element={<Board />} />
          <Route path="/board/:postId" element={<PostDetail />} />
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
