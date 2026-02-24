import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { connectRoundQrStream } from '../../../utils/qrManage';

const QrRenderPage = () => {
  const [searchParams] = useSearchParams();
  const roundId = searchParams.get('roundId');
  const [qrData, setQrData] = useState(null);
  const [error, setError] = useState(null);
  useEffect(() => {
    if (!roundId) {
      setError('roundId가 없습니다.');
      return;
    }

    const es = connectRoundQrStream(
      roundId,
      (data) => {
        setQrData(data);
      },
      (err) => {
        setError('QR 스트림 연결에 실패했습니다.');
      }
    );

    return () => es.close();
  }, [roundId]);

  return (
    <div style={{ textAlign: 'center', marginTop: '80px' }}>
      <h1>QR 코드</h1>

      {error ? (
        <p style={{ color: 'red' }}>{error}</p>
      ) : qrData ? (
        <QRCodeSVG value={qrData.qrToken} size={400} />
      ) : (
        <p>QR 생성 중...</p>
      )}
    </div>
  );
};

export default QrRenderPage;
