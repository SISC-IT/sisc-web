import styles from './Home.module.css';
import Coin3 from '../assets/coin3.png';
import Coin4 from '../assets/coin4.svg';
import Coin5 from '../assets/coin5.png';

const Home = () => {
  return (
    <div className={styles.container}>
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
