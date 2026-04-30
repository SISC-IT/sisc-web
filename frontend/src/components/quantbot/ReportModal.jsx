// src/components/quantbot/ReportModal.jsx
import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import './ReportModal.css';

export default function ReportModal({ open, onClose, report }) {
  if (!open) return null;

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-container"
        onClick={(e) => e.stopPropagation()} // 배경 클릭만 닫히게
      >
        <div className="modal-header">
          <h2>XAI 리포트</h2>
          <button className="modal-close" onClick={onClose}>
            ✕
          </button>
        </div>

        {!report && (
          <div className="modal-loading">리포트를 불러오는 중...</div>
        )}

        {report && (
          <div className="modal-content">
            {/* 왼쪽 메타 정보 */}
            <div className="modal-left">
              <div className="modal-item">
                <span className="label">티커</span>
                <span className="value">{report.displayTicker || report.ticker}</span>
              </div>
              <div className="modal-item">
                <span className="label">시그널</span>
                <span className="value">{report.signal}</span>
              </div>
              <div className="modal-item">
                <span className="label">가격</span>
                <span className="value">{report.price}</span>
              </div>
              <div className="modal-item">
                <span className="label">날짜</span>
                <span className="value">{report.date}</span>
              </div>
            </div>

            {/* 오른쪽 마크다운 리포트 */}
            <div className="modal-right">
              <div className="report-title">📘 분석 리포트</div>
              <div className="report-body">
                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                  {report.report || ''}
                </ReactMarkdown>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
