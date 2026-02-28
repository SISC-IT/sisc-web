// import { useEffect, useState } from 'react';
// import { useNavigate, useSearchParams } from 'react-router-dom';
// // import api from '../../../utils/attendanceManage';

// const CheckInPage = () => {
//   const [searchParams] = useSearchParams();
//   const token = searchParams.get('token');
//   const navigate = useNavigate();
//   const [message, setMessage] = useState('출석 처리 중...');

//   useEffect(() => {
//     const accessToken = localStorage.getItem('accessToken');

//     if (!accessToken) {
//       navigate(`/login?returnUrl=${encodeURIComponent(window.location.href)}`);
//       return;
//     }

//     const checkIn = async () => {
//       try {
//         await api.post('/api/attendance/check-in', {
//           token,
//         });

//         setMessage('출석이 완료되었습니다!');
//         setTimeout(() => navigate('/'), 2000);
//       } catch (err) {
//         setMessage(
//           err.response?.data?.message || '이미 출석했거나 만료된 QR입니다.'
//         );
//       }
//     };

//     if (token) {
//       checkIn();
//     } else {
//       setMessage('잘못된 접근입니다.');
//     }
//   }, [token, navigate]);

//   return (
//     <div style={{ textAlign: 'center', marginTop: '100px' }}>
//       <h2>{message}</h2>
//     </div>
//   );
// };

// export default CheckInPage;
