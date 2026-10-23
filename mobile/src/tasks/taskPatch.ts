// Edit sends ONLY the fields that changed, plus the row version (Route plan 3.6, UI plan 7.10).
// Pure function, so it's unit-tested directly.
import type { Category, TaskChanges } from '../api/types';

export type EditableValues = {
  title: string;
  category: string;
  hours: number;
  deadline: Date;
  description: string; // "" = no description
};

export function buildTaskPatch(original: EditableValues, edited: EditableValues): TaskChanges {
  const patch: TaskChanges = {};
  if (edited.title.trim() !== original.title.trim()) patch.title = edited.title.trim();
  if (edited.category !== original.category) patch.category = edited.category as Category;
  if (edited.hours !== original.hours) patch.estimatedHours = edited.hours;
  // Unchanged deadlines are never re-sent: an overdue task's past deadline would be rejected by the server
  if (edited.deadline.getTime() !== original.deadline.getTime()) patch.deadline = edited.deadline.toISOString();
  // An emptied description is sent as "" so the server clears it
  if (edited.description.trim() !== original.description.trim()) patch.description = edited.description.trim();
  return patch;
}
