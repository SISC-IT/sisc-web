import { toast } from 'react-toastify';

const confirmToastIds = new Set();

export function toastConfirm(
  message,
  {
    title = '확인',
    confirmText = '확인',
    cancelText = '취소',
    autoClose = false,
  } = {}
) {
  return new Promise((resolve) => {
    const id = toast(
      ({ closeToast }) => {
        const handleConfirm = () => {
          confirmToastIds.delete(id);
          resolve(true);
          closeToast();
        };

        const handleCancel = () => {
          confirmToastIds.delete(id);
          resolve(false);
          closeToast();
        };

        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {title && (
              <div
                style={{
                  fontWeight: 600,
                  fontSize: '0.9rem',
                  color: '#111827',
                }}
              >
                {title}
              </div>
            )}
            <div style={{ fontSize: '0.85rem', color: '#4b5563' }}>
              {message}
            </div>
            <div
              style={{
                display: 'flex',
                justifyContent: 'flex-end',
                gap: 8,
                marginTop: 6,
              }}
            >
              <button
                type="button"
                onClick={handleCancel}
                style={{
                  padding: '4px 10px',
                  fontSize: '0.8rem',
                  borderRadius: 6,
                  border: '1px solid #e5e7eb',
                  backgroundColor: '#ffffff',
                  cursor: 'pointer',
                }}
              >
                {cancelText}
              </button>
              <button
                type="button"
                onClick={handleConfirm}
                style={{
                  padding: '4px 10px',
                  fontSize: '0.8rem',
                  borderRadius: 6,
                  border: 'none',
                  backgroundColor: '#c53030',
                  color: '#ffffff',
                  cursor: 'pointer',
                }}
              >
                {confirmText}
              </button>
            </div>
          </div>
        );
      },
      {
        autoClose,
        closeOnClick: false,
        pauseOnHover: true,
        draggable: false,
      }
    );

    confirmToastIds.add(id);
  });
}

/**
 * toastConfirm 으로 생성된 confirm 토스트만 모두 닫기
 */
export function dismissConfirmToasts() {
  confirmToastIds.forEach((id) => {
    toast.dismiss(id);
  });
  confirmToastIds.clear();
}
