import { api } from '../../utils/axios';

// 템플릿 목록 조회 (GET /api/backtest/templates)
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

// 템플릿 생성 (POST /api/backtest/templates)
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

// 백테스트 결과를 템플릿에 저장 (PATCH /api/backtest/runs/{backtestRunId})
export async function saveBacktestRunToTemplate(backtestRunId, payload) {
  const res = await api.patch(`/api/backtest/runs/${backtestRunId}`, payload);
  return res.data;
}

// 특정 템플릿 상세 + 해당 템플릿에 저장된 backtestRun 목록 조회 (GET /api/backtest/templates/{templateId})
export async function fetchBacktestTemplateDetail(templateId) {
  const res = await api.get(`/api/backtest/templates/${templateId}`);

  const template = res.data?.template ?? null;
  const backtestRunsRaw = res.data?.backtestRuns ?? [];

  const runs = Array.isArray(backtestRunsRaw)
    ? backtestRunsRaw.map((run) => ({
        id: run.id,
        title: run.title,
        startDate: run.startDate,
        endDate: run.endDate,
        status: run.status,
      }))
    : [];

  return { template, runs };
}

// 백테스트 실행 결과 삭제 (DELETE /api/backtest/runs/{backtestRunId})
export async function deleteBacktestRun(backtestRunId) {
  const res = await api.delete(`/api/backtest/runs/${backtestRunId}`, {
    data: { backtestRunId },
  });
  return res.data;
}
