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

// 템플릿 제목 수정 (PATCH /api/backtest/templates/{templateId})
export async function patchBacktestTemplateTitle(templateId, title) {
  const res = await api.patch(`/api/backtest/templates/${templateId}`, {
    title,
  });
  // 백엔드 응답 형식에 따라 맞게 수정해도 됨
  return res.data;
}

// 템플릿 삭제 (DELETE /api/backtest/templates/{templateId})
export async function deleteBacktestTemplate(templateId) {
  const res = await api.delete(`/api/backtest/templates/${templateId}`);
  return res.data;
}
