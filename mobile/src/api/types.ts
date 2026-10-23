// App-side shapes of what the backend returns. The backend's JSON names are used as-is where they
// already match; where they differ from the UI plan's proposals, the backend's names win (UI plan 0).

export type Me = {
  fullName: string;
  username: string;
  email: string | null;
  emailVerified: boolean;
};

export type RegisterInput = {
  fullName: string;
  username: string;
  password: string;
  email?: string;
};

export const CATEGORIES = [
  'ASSIGNMENT', 'PROJECT', 'EXAM', 'QUIZ', 'LAB', 'PRESENTATION',
  'MEETING', 'LEARNING', 'PERSONAL', 'ADMINISTRATIVE', 'UNCATEGORIZED',
] as const; // 11 exactly, no STUDY
export type Category = (typeof CATEGORIES)[number];

// Most urgent first: the order the ladder and the dashboard show them
export const PRIORITIES = ['DUE_TOMORROW', 'HIGH', 'MEDIUM', 'LOW'] as const;
export type Priority = (typeof PRIORITIES)[number];

export type TaskStatus = 'PENDING' | 'COMPLETED';
export type TaskScope = 'DUE_SOON' | 'OVERDUE';
export type TaskSort = 'PRIORITY' | 'DEADLINE';

export type Task = {
  id: number;
  title: string;
  description: string | null;
  category: Category;
  deadline: string; // ISO-8601 instant (UTC)
  estimatedHours: number;
  initialPriority: Priority;
  currentPriority: Priority;
  status: TaskStatus;
  overdue: boolean; // computed by the server's clock, never the phone's
  dueSoon: boolean;
  version: number; // row version, sent back with every change (optimistic locking)
  createdAt: string;
  updatedAt: string;
};

export type Page<T> = { items: T[]; page: number; hasNext: boolean };

// Counts of ALL pending tasks; filters never change them (Route plan 3.4)
export type Summary = { pending: Record<Priority, number>; dueSoon: number; overdue: number };

export type Reminder = { taskId: number; title: string; reason: string };

export type Suggestion = { title: string; category: Category; estimatedHours: number };

export type NewTask = {
  title: string;
  category: Category;
  estimatedHours: number;
  deadline: string;
  description?: string;
};
