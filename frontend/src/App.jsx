import './App.css';
import { Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Home from './pages/Home';
import Attendance from './pages/Attendance';
import Board from './pages/Board';
import StockGame from './pages/StockGame';
import BackTest from './pages/BackTest';
import Mypage from './pages/Mypage';

function App() {
  return (
    <>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<Home />} />
          <Route path="/attendance" element={<Attendance />} />
          <Route path="/board" element={<Board />} />
          <Route path="/stock-game" element={<StockGame />} />
          <Route path="/back-test" element={<BackTest />} />
          <Route path="/mypage" element={<Mypage />} />
        </Route>
      </Routes>
    </>
  );
}

export default App;
