import styles from './Main.module.css';
import image from '../../assets/external-image.png';
import Logo from '../../assets/logo.png';
import { Link } from 'react-router-dom';

const Main = () => {
  return (
    <div className={styles.container}>
      <img src={image} alt="메인 사진" className={styles.image} />
      <nav className={styles.menu}>
        <ul>
          <li>
            <Link to="/main/intro">동아리 소개</Link>
          </li>
          <li>
            <Link to="/main/leaders">임원소개</Link>
          </li>
          <li>
            <Link to="/main/portfolio">운용 포트폴리오</Link>
          </li>
          <li>
            <Link to="/">웹사이트</Link>
          </li>
        </ul>
      </nav>
      <div className={styles.info}>
        <img src={Logo} alt="로고" className={styles.logo} />
        <h1 className={styles.title}>Sejong Investment Scholars Club</h1>
        <h2 className={styles.subTitle}>
          세투연과 세상을 읽고 미래에 투자하라
        </h2>
      </div>
    </div>
  );
};

export default Main;
