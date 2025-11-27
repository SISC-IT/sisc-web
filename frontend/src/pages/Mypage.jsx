import styles from './Mypage.module.css';
import MyPageMenu from '../components/mypage/MyPageMenu';
import ProfileCard from '../components/mypage/ProfileCard';
import AccountSecurity from '../components/mypage/AccountSecurity';
import { useAuthGuard } from '../hooks/useAuthGuard';

const Mypage = () => {
  useAuthGuard();

  return (
    <div className={styles.container}>
      <h1 className={styles.title}>마이페이지</h1>

      <ProfileCard />
      <MyPageMenu />
      <AccountSecurity />
    </div>
  );
};

export default Mypage;
