import styles from './Home.module.css';
import Coin3 from '../assets/coin3.png';
import Coin4 from '../assets/coin4.svg';
import Coin5 from '../assets/coin5.png';
import { useNavigate } from 'react-router-dom';
import { useEffect, useState } from 'react';

const Home = () => {
  const nav = useNavigate();
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [userNickname, setUserNickname] = useState('');

  useEffect(() => {
    // localStorage에서 토큰 확인
    const token = localStorage.getItem('accessToken');
    const nickname = localStorage.getItem('userNickname');

    if (token) {
      setIsLoggedIn(true);
      setUserNickname(nickname || '사용자');
      console.log('✅ 로그인 상태 감지:', {
        nickname: nickname || '사용자',
        timestamp: new Date().toLocaleString('ko-KR'),
      });
    } else {
      console.log('❌ 로그인하지 않은 상태');
    }
  }, []);

  const handleLogout = async () => {
    console.log('📋 로그아웃 시작');
    const accessToken = localStorage.getItem('accessToken');

    if (!accessToken) {
      console.log('⚠️ accessToken이 없습니다');
      setIsLoggedIn(false);
      setUserNickname('');
      nav('/');
      return;
    }

    try {
      console.log('🔄 백엔드 로그아웃 API 호출 중...');
      const response = await fetch('http://localhost:8080/api/auth/logout', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
      });

      if (response.ok) {
        const data = await response.json();
        console.log('✅ 로그아웃 성공:', data);
      } else {
        console.warn('⚠️ 서버 응답 오류:', response.status, response.statusText);
      }
    } catch (err) {
      console.error('❌ 로그아웃 API 오류:', err.message);
    } finally {
      // 성공/실패 관계없이 localStorage 초기화 (멱등성 보장)
      console.log('🧹 localStorage 초기화 중...');
      localStorage.removeItem('accessToken');
      localStorage.removeItem('refreshToken');
      localStorage.removeItem('userNickname');
      setIsLoggedIn(false);
      setUserNickname('');
      console.log('✅ 클라이언트 로그아웃 완료 - 홈으로 이동 중...');
      nav('/');
    }
  };

  return (
    <div className={styles.container}>
      <header className={styles.header}>
        {isLoggedIn ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: '20px' }}>
            <span className={styles.userInfo}>{userNickname}님</span>
            <div style={{ display: 'flex', gap: '10px', fontSize: '14px' }}>
              <button
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#666' }}
                onClick={() => nav('/mypage')}
              >
                마이페이지
              </button>
              <span style={{ color: '#ccc' }}>|</span>
              <button
                style={{ background: 'none', border: 'none', cursor: 'pointer', color: '#666' }}
                onClick={handleLogout}
              >
                로그아웃
              </button>
            </div>
          </div>
        ) : (
          <>
            <button className={styles.login} onClick={() => nav('/login')}>
              로그인
            </button>
            <button className={styles.signUp} onClick={() => nav('signup')}>
              회원가입
            </button>
          </>
        )}
      </header>
      <div className={styles.upper}></div>
      <div className={styles.lower}></div>

      <div className={styles.textBox}>
        <h1>
          Sejong Investment <br />
          Scholars Club
        </h1>
        <h2>세투연과 함께 세상을 읽고 미래에 투자하라</h2>
        <div className={styles.imgBox}>
          <img src={Coin3} alt="코인 이미지" className={styles.coin3} />
          <img src={Coin4} alt="코인 이미지" className={styles.coin4} />
          <img src={Coin5} alt="코인 이미지" className={styles.coin5} />
        </div>
      </div>
    </div>
  );
};

export default Home;
