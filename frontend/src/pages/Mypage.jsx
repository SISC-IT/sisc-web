import styles from './Mypage.module.css';
import MyPageMenu from '../components/mypage/MyPageMenu';
import ProfileCard from '../components/mypage/ProfileCard';
import AccountSecurity from '../components/mypage/AccountSecurity';
import { useEffect } from 'react';
import { useNavigate } from 'react-router-dom';

const Mypage = () => {
  const nav = useNavigate();
  useEffect(() => {
    if (!localStorage.getItem('accessToken')) {
      alert('로그인 후 이용하실 수 있습니다.');
      nav('/login');
    }
  }, []);

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
