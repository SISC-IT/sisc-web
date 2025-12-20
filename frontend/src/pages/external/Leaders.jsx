import styles from './External.module.css';
import Filter from '../../components/external/Filter';
import MemberCard from '../../components/external/MemberCard';
import { useState } from 'react';
import { executivesByGeneration } from '../../utils/executiveByGeneration';

const cohort = Array.from({ length: 24 }, (_, i) => `${24 - i}기`);

const Leaders = () => {
  const [selected, setSelected] = useState(cohort[0]);
  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <span className={styles.title}>임원진 소개</span>
        <hr className={styles.divider} />
      </div>
      <div className={styles.info}>
        <div className={styles.filter}>
          <Filter items={cohort} value={selected} onChange={setSelected} />
        </div>
        <MemberCard datas={executivesByGeneration[selected]} />
      </div>
    </div>
  );
};

export default Leaders;
