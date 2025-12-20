import styles from './External.module.css';
import Filter from '../../components/external/Filter';
import Info from '../../components/external/Info';
import { useState } from 'react';
import Logo from '../../assets/sejong_logo.png';

const teams = [
  '증권 1팀',
  '증권 2팀',
  '증권 3팀',
  '자산 운용팀',
  '금융 IT팀',
  '매크로팀',
  '트레이딩팀',
];

const Intro = () => {
  const [selected, setSelected] = useState(teams[0]);

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.title}>동아리 소개</span>
        <hr className={styles.divider} />
      </div>
      <div className={styles.info}>
        <div className={styles.filter}>
          <div className={styles.logoSection}>
            <img src={Logo} alt="세종투자연구회" className={styles.logo} />
            <span className={styles.name}>Sejong Investment Scholars Club</span>
          </div>
          <Filter items={teams} value={selected} onChange={setSelected} />
        </div>
        <div className={styles.content}>
          <Info team={selected} />
        </div>
      </div>
    </div>
  );
};

export default Intro;
