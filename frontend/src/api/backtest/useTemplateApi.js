import { api } from '../../utils/axios';

// 템플릿 목록 조회
export async function fetchBacktestTemplates() {
  const res = await api.get('/api/backtest/templates');
  const raw = res.data?.templates;
  if (!Array.isArray(raw)) return [];

  return raw.map((t) => ({
    templateId: t.templateId,
    title: t.title,
    updatedDate: t.updatedDate,
    isPublic: t.isPublic,
    description: t.description,
  }));
}

export async function createBacktestTemplate(title) {
  const body = {
    title,
    description: 'empty',
    isPublic: false,
  };

  const res = await api.post('/api/backtest/templates', body);
  return res.data;
}

// 템플릿 제목 수정 (PATCH /api/backtest/templates/{templateId})
export async function patchBacktestTemplateTitle(templateId, title) {
  const res = await api.patch(`/api/backtest/templates/${templateId}`, {
    templateId,
    title,
  });
  return res.data;
}

// 템플릿 삭제 (DELETE /api/backtest/templates/{templateId})
export async function deleteBacktestTemplate(templateId) {
  const res = await api.delete(`/api/backtest/templates/${templateId}`);
  return res.data;
}

// 백테스트 결과를 템플릿에 저장 (POST /api/backtest/runs/{backtestRunId})
export async function saveBacktestRunToTemplate(backtestRunId, payload) {
  const res = await api.patch(`/api/backtest/runs/${backtestRunId}`, payload);
  return res.data;
}
