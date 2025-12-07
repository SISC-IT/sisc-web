import { api } from '../../utils/axios';

// 템플릿 목록 조회
export async function fetchBacktestTemplates() {
  const res = await api.get('/api/backtest/templates');
  const raw = res.data?.templates;
  if (!Array.isArray(raw)) return [];

  return raw.map((t) => ({
    templateId: t.templateId,
    name: t.title,
    updatedAt: t.updatedDate,
    isPublic: t.isPublic,
    description: t.description,
  }));
}
