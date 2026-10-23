// Task, summary, reminder and AI routes (Route plan sections 3 and 4)
import { request } from './client';
import { Category, NewTask, Page, Priority, Reminder, Suggestion, Summary, Task, TaskChanges, TaskScope, TaskSort, TaskStatus } from './types';

export type TaskFilters = {
  status: TaskStatus;
  priority?: Priority | null;
  category?: Category | null;
  scope?: TaskScope | null;
  sort?: TaskSort;
};

export function listTasks(filters: TaskFilters, page: number): Promise<Page<Task>> {
  const query = new URLSearchParams({ status: filters.status, page: String(page) });
  if (filters.priority) query.set('priority', filters.priority);
  if (filters.category) query.set('category', filters.category);
  if (filters.scope) query.set('scope', filters.scope);
  if (filters.sort && filters.sort !== 'PRIORITY') query.set('sort', filters.sort);
  return request(`/tasks?${query}`);
}

export const getSummary = () => request<Summary>('/tasks/summary');
// One task with its files; a missing task and someone else's task both come back as NOT_AVAILABLE
export const getTask = (id: number) => request<Task>(`/tasks/${id}`);
export const updateTask = (id: number, changes: TaskChanges, version: number) =>
  request<Task>(`/tasks/${id}`, { method: 'PATCH', body: { ...changes, version } });
export const getReminders = () => request<Reminder[]>('/reminders');

// Every change sends the row version the app last saw; a stale one comes back as CONFLICT (409)
export const completeTask = (id: number, version: number) =>
  request<Task>(`/tasks/${id}/complete`, { method: 'POST', body: { version } });
export const reopenTask = (id: number, version: number) =>
  request<Task>(`/tasks/${id}/reopen`, { method: 'POST', body: { version } });
export const rescheduleTask = (id: number, deadline: string, version: number) =>
  request<Task>(`/tasks/${id}`, { method: 'PATCH', body: { deadline, version } });
export const deleteTask = (id: number, version: number) =>
  request<void>(`/tasks/${id}?version=${version}`, { method: 'DELETE' });

// Only the description is ever sent to the AI step: never the deadline (SRS 8.6)
export const analyzeDescription = (description: string, signal?: AbortSignal) =>
  request<Suggestion>('/tasks/analyze', { method: 'POST', body: { description }, signal });

// No priority field: the server computes it (Route plan 3.2)
export const createTask = (task: NewTask) => request<Task>('/tasks', { method: 'POST', body: task });
