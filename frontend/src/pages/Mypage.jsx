import styles from './Mypage.module.css';
import MyPageMenu from '../components/mypage/MyPageMenu';
import ProfileCard from '../components/mypage/ProfileCard';

const Mypage = () => {
  return (
    <div className={styles.container}>
      <h1 className={styles.title}>마이페이지</h1>

      <ProfileCard />
      <MyPageMenu />
    </div>
  );
};

export default Mypage;
