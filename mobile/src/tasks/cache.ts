import { QueryClient } from '@tanstack/react-query';

// After any create, complete, reopen, reschedule, edit or delete: refetch lists, counts and reminders (UI plan 8.3)
export function refreshTaskData(queryClient: QueryClient) {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: ['tasks'] }),
    queryClient.invalidateQueries({ queryKey: ['summary'] }),
    queryClient.invalidateQueries({ queryKey: ['reminders'] }),
  ]);
}
