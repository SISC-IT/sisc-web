import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { QRCodeSVG } from 'qrcode.react';
import { connectRoundQrStream } from '../../../utils/qrManage';

const QrRenderPage = () => {
  const [searchParams] = useSearchParams();
  const roundId = searchParams.get('roundId');
  const [qrData, setQrData] = useState(null);

  useEffect(() => {
    if (!roundId) return;

    const es = connectRoundQrStream(roundId, (data) => {
      setQrData(data);
    });

    return () => es.close();
  }, [roundId]);

  return (
    <div style={{ textAlign: 'center', marginTop: '80px' }}>
      <h1>QR 코드</h1>

      {qrData ? (
        <QRCodeSVG value={qrData.qrToken} size={400} />
      ) : (
        <p>QR 생성 중...</p>
      )}
    </div>
  );
};

export default QrRenderPage;
