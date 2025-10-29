import styles from './Home.module.css';
import Coin3 from '../assets/coin3.png';
import Coin4 from '../assets/coin4.svg';
import Coin5 from '../assets/coin5.png';
import Logo from '../assets/logo.png';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Sidebar from '../components/Sidebar';

const Home = () => {
  const [isOpen, setIsOpen] = useState(false);
  const nav = useNavigate();

  const toggleSidebar = () => {
    setIsOpen(!isOpen);
  };

  return (
    <div className={styles.page}>
      {isOpen && (
        <div className={styles.sidebar}>
          <Sidebar />
        </div>
      )}
      <div className={styles.container}>
        <div className={styles.upper}></div>
        <div className={styles.lower}></div>
        <header className={styles.header}>
          <div className={styles.left}>
            <button className={styles.menuButton} onClick={toggleSidebar}>
              <span></span>
              <span></span>
              <span></span>
            </button>
            <div className={styles.brand}>
              <img
                className={styles.logo}
                src={Logo}
                alt="세종투자연구회 로고"
              />
              <span className={styles.title}>세종투자연구회</span>
            </div>
          </div>
          <div className={styles.right}>
            <button className={styles.login} onClick={() => nav('/login')}>
              로그인
            </button>
            <button className={styles.signUp} onClick={() => nav('signup')}>
              회원가입
            </button>
          </div>
        </header>
        <div className={styles.textBox}>
          <h1>
            Sejong Investment <br />
            Scholars Club
          </h1>
          <h2>세투연과 함께 세상을 읽고 미래에 투자하라</h2>
        </div>
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
