import { useState } from 'react';
import { Upload, FileSpreadsheet, Loader2, Trash2, CheckCircle } from 'lucide-react';
import { toast } from 'react-toastify';
import { uploadAdminUsersExcel } from '../../utils/adminUserApi';
import AdminExcelUploadHeader from './AdminExcelUploadHeader';
import styles from './AdminExcelUpload.module.css';

const ALLOWED_EXTENSIONS = ['.xlsx', '.xls'];

const isExcelFile = (targetFile) => {
  if (!targetFile) return false;
  const lowerName = targetFile.name.toLowerCase();
  return ALLOWED_EXTENSIONS.some((ext) => lowerName.endsWith(ext));
};

const AdminExcelUpload = () => {
  const [selectedFile, setSelectedFile] = useState(null);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadResult, setUploadResult] = useState(null);
  const [isDragOver, setIsDragOver] = useState(false);

  const handleSelectFile = (file) => {
    if (!file) return;

    if (!isExcelFile(file)) {
      toast.error('엑셀 파일(.xlsx, .xls)만 업로드할 수 있습니다.');
      return;
    }

    setSelectedFile(file);
    setUploadResult(null);
  };

  const handleInputChange = (event) => {
    const file = event.target.files?.[0];
    handleSelectFile(file);
    event.target.value = '';
  };

  const handleDragOver = (event) => {
    event.preventDefault();
    setIsDragOver(true);
  };

  const handleDragLeave = (event) => {
    event.preventDefault();
    setIsDragOver(false);
  };

  const handleDrop = (event) => {
    event.preventDefault();
    setIsDragOver(false);
    const file = event.dataTransfer.files?.[0];
    handleSelectFile(file);
  };

  const handleUpload = async () => {
    if (!selectedFile) {
      toast.error('업로드할 파일을 먼저 선택해주세요.');
      return;
    }

    setIsUploading(true);
    try {
      const result = await uploadAdminUsersExcel({ file: selectedFile });
      setUploadResult(result);
      toast.success('엑셀 명단 업로드 및 동기화가 완료되었습니다.');
    } catch (error) {
      toast.error(error?.response?.data?.message || error?.message || '엑셀 업로드에 실패했습니다.');
    } finally {
      setIsUploading(false);
    }
  };

  const resetFile = () => {
    setSelectedFile(null);
    setUploadResult(null);
  };

  const handleDownloadTemplate = () => {
    toast.info('템플릿 다운로드 기능은 준비 중입니다.');
  };

  return (
    <div className={styles.container}>
      <AdminExcelUploadHeader onDownloadTemplate={handleDownloadTemplate} />

      <section className={styles.panel}>
        <h2 className={styles.title}>엑셀 명단 업로드 및 동기화</h2>
        <p className={styles.description}>
          회원 엑셀 파일을 업로드하면 서버에서 전체 동기화를 진행합니다.
        </p>

        <div
          className={`${styles.uploadBox} ${isDragOver ? styles.uploadBoxDragOver : ''}`}
          onDragOver={handleDragOver}
          onDragLeave={handleDragLeave}
          onDrop={handleDrop}
        >
          <FileSpreadsheet size={40} />
          <p className={styles.uploadText}>.xlsx 또는 .xls 파일을 드래그 앤 드롭하거나 선택하세요.</p>

          <label className={styles.fileLabel}>
            파일 선택
            <input
              type="file"
              accept=".xlsx,.xls"
              onChange={handleInputChange}
              className={styles.fileInput}
            />
          </label>

          {selectedFile && (
            <div className={styles.selectedRow}>
              <span className={styles.fileName}>{selectedFile.name}</span>
              <button type="button" className={styles.ghostButton} onClick={resetFile}>
                <Trash2 size={14} />
                제거
              </button>
            </div>
          )}

          <button
            type="button"
            className={styles.uploadButton}
            onClick={handleUpload}
            disabled={!selectedFile || isUploading}
          >
            {isUploading ? (
              <>
                <Loader2 size={16} className={styles.spin} />
                업로드 중...
              </>
            ) : (
              <>
                <Upload size={16} />
                업로드 실행
              </>
            )}
          </button>
        </div>
      </section>

      {uploadResult && (
        <section className={styles.panel}>
          <div className={styles.resultTitleWrap}>
            <CheckCircle size={18} />
            <h3 className={styles.resultTitle}>업로드 결과</h3>
          </div>
          <pre className={styles.resultBox}>
            {JSON.stringify(uploadResult, null, 2)}
          </pre>
        </section>
      )}
    </div>
  );
};

export default AdminExcelUpload;
