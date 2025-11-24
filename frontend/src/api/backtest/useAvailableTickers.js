import { useEffect, useState } from 'react';
import { api } from '../../utils/axios';

export default function useAvailableTickers() {
  const [availableTickers, setAvailableTickers] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    let cancelled = false;

    async function fetchTickers() {
      setIsLoading(true);
      setError(null);

      try {
        const res = await api.get('/api/backtest/stocks/info');
        const data = res.data;

        if (!cancelled && data && Array.isArray(data.availableTickers)) {
          setAvailableTickers(data.availableTickers);
        }
      } catch (e) {
        if (!cancelled) {
          setError(e);
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    }

    fetchTickers();

    return () => {
      cancelled = true;
    };
  }, []);

  return { availableTickers, isLoading, error };
}
