import { useState, useEffect } from 'react';
import styles from './ProfileCard.module.css';
import ProfileImage from '../../assets/profile-image.png';
import Coin4 from '../../assets/coin4.svg';
import { api } from '../../utils/axios';
import { toast } from 'react-toastify';
import AccountSecurity from './AccountSecurity';

const ProfileCard = () => {
  const [userName, setUserName] = useState('로딩 중...');
  const [userPoint, setUserPoint] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  // 사용자 정보 불러오기
  useEffect(() => {
    const fetchUserInfo = async () => {
      try {
        const response = await api.get('/api/user/details');
        const { name, point } = response.data;
        setUserName(name || '사용자');
        setUserPoint(point || 0);
      } catch (error) {
        console.error('사용자 정보 불러오기 실패:', error);
        toast.error('사용자 정보를 불러올 수 없습니다.');
        setUserName('사용자');
      } finally {
        setIsLoading(false);
      }
    };

    fetchUserInfo();
  }, []);

  return (
    <section className={styles.profileRow} aria-label="프로필">
      <div>
        <img src={ProfileImage} alt="프로필 이미지" />
      </div>
      <div>
        <div className={styles.textWrap}>
          <div className={styles.nameRow}>
            <h2 className={styles.username}>{userName}</h2>
          </div>
          <div className={styles.metaRow}>
            <button
              type="button"
              className={styles.gradeBtn}
              aria-label="등급 보기"
            >
              일반 등급
            </button>

            <div className={styles.metaDivider} aria-hidden="true" />

            <div className={styles.points} aria-label="포인트">
              <img
                src={Coin4}
                alt="포인트 아이콘"
                className={styles.pointsIcon}
              />
              <p className={styles.pointsText}>{userPoint}P</p>
            </div>
          </div>
        </div>
        <AccountSecurity />
      </div>
    </section>
  );
};

export default ProfileCard;
