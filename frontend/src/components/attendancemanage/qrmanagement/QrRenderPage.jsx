import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import styles from '../SessionManagementCard.module.css';
import { connectRoundQrStream } from '../../../utils/qrManage';

const QrRenderPage = () => {
  const [searchParams] = useSearchParams();
  const roundId = searchParams.get('roundId');
  const [qrToken, setQrToken] = useState(null);

  useEffect(() => {
    const eventSource = connectRoundQrStream(
      roundId,
      (token) => setQrToken(token),
      () => console.log('SSE error')
    );

    return () => eventSource.close();
  }, [roundId]);

  const qrUrl = qrToken
    ? `${window.location.origin}/attendance/check-in?token=${qrToken}`
    : '';

  return (
    <div className={styles.container}>
      <h1>QR 코드</h1>
      {qrToken ? <QRCodeSVG value={qrUrl} size={400} /> : <p>QR 생성 중...</p>}
    </div>
  );
};

export default QrRenderPage;
