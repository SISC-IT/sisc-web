import { useEffect, useState } from 'react';

const BASE_URL = import.meta.env.VITE_API_URL;

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
        const res = await fetch(`${BASE_URL}/api/backtest/stocks/info`);
        if (!res.ok) {
          throw new Error(`HTTP ${res.status}`);
        }
        const data = await res.json();

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
