import { useEffect, useState, useCallback } from 'react';
import { fetchBacktestTemplates } from './getTemplateList';

export function useBacktestTemplates() {
  const [templates, setTemplates] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  const loadTemplates = useCallback(async () => {
    setIsLoading(true);
    setError(null);
    try {
      const data = await fetchBacktestTemplates();
      setTemplates(data);
    } catch (err) {
      console.error('Failed to load backtest templates', err);
      setError(err);
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    loadTemplates();
  }, [loadTemplates]);

  return {
    templates,
    isLoading,
    error,
    reload: loadTemplates,
    setTemplates, // 필요하면 외부에서 직접 수정할 수 있게
  };
}
