import { Suggestion } from '@/api/types';

// The description behind the last successful AI suggestion, in memory only (UI plan 9, SRS 8.6 rule 9).
// Tapping "Analyze task" again with the same text reuses it: zero tokens, no network call.
// shortcut: reuses the suggestion, not edits made on the review screen; keep edits too if users ask.
let last: { text: string; suggestion: Suggestion } | null = null;

export function rememberAnalysis(text: string, suggestion: Suggestion) {
  last = { text, suggestion };
}

export function recallAnalysis(text: string): Suggestion | null {
  return last && last.text === text ? last.suggestion : null;
}

// After a task is saved and on log out (UI plan 9 rule 7)
export function clearAnalysis() {
  last = null;
}
