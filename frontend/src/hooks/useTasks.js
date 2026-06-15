import { useState, useEffect } from 'react';
import { fetchTasks } from '../api';

export function useTasks(query, status, page, pageSize) {
  const [tasks, setTasks] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  useEffect(() => {
    setLoading(true);
    setError(null);

    fetchTasks({ query, status, page, pageSize })
      .then((data) => {
        setTasks(data.items);
        setTotal(data.total);
      })
      .catch((err) => {
        // BUG FIX: setLoading(false) was missing in catch block.
        // If API call failed, loading stayed true forever and error never showed.
        setError(err.message);
        setTasks([]);
        setTotal(0);
      })
      .finally(() => {
        // Always clear loading whether success or failure
        setLoading(false);
      });
  }, [query, status, page, pageSize]);

  return { tasks, total, loading, error };
}
