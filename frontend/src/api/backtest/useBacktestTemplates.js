import { useEffect, useState, useCallback } from 'react';
import { fetchBacktestTemplates } from './useTemplateApi';

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
    setTemplates,
  };
}
