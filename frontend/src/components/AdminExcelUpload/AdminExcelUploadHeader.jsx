import { Download, Info } from 'lucide-react';
import styles from './AdminExcelUpload.module.css';

const AdminExcelUploadHeader = ({ onDownloadTemplate }) => {
  return (
    <section className={styles.panel}>
      <div className={styles.guideHeader}>
        <Info size={18} />
        <h2 className={styles.guideTitle}>사용 안내</h2>
      </div>

      <div className={styles.guideGrid}>
        <div className={styles.guideItem}>
          <div className={styles.guideStep}>1</div>
          <div>
            <p className={styles.guideItemTitle}>템플릿 다운로드</p>
            <p className={styles.guideItemDesc}>아래 버튼으로 엑셀 템플릿을 다운로드하세요.</p>
          </div>
        </div>

        <div className={styles.guideItem}>
          <div className={styles.guideStep}>2</div>
          <div>
            <p className={styles.guideItemTitle}>데이터 입력</p>
            <p className={styles.guideItemDesc}>템플릿에 맞춰 회원 정보를 입력하세요.</p>
          </div>
        </div>

        <div className={styles.guideItem}>
          <div className={styles.guideStep}>3</div>
          <div>
            <p className={styles.guideItemTitle}>파일 업로드</p>
            <p className={styles.guideItemDesc}>완성된 파일을 업로드해 동기화를 진행하세요.</p>
          </div>
        </div>
      </div>

      <div className={styles.guideActionRow}>
        <button type="button" className={styles.ghostButton} onClick={onDownloadTemplate}>
          <Download size={14} />
          회원 등록 템플릿 다운로드 (.xlsx)
        </button>
      </div>
    </section>
  );
};

export default AdminExcelUploadHeader;