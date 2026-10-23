import { buildTaskPatch, EditableValues } from '../taskPatch';

const original: EditableValues = {
  title: 'Lab report',
  category: 'LAB',
  hours: 2,
  deadline: new Date('2026-10-20T10:00:00Z'), // already in the past: an overdue task
  description: 'Section 3',
};

describe('buildTaskPatch', () => {
  it('sends nothing when nothing changed', () => {
    expect(buildTaskPatch(original, { ...original, title: ' Lab report ' })).toEqual({});
  });

  it('sends only the changed fields, never an unchanged (past) deadline', () => {
    expect(buildTaskPatch(original, { ...original, title: 'Lab report v2', hours: 2.5 })).toEqual({ title: 'Lab report v2', estimatedHours: 2.5 });
  });

  it('sends a new deadline and category as they are', () => {
    const deadline = new Date('2026-11-01T18:30:00Z');
    expect(buildTaskPatch(original, { ...original, deadline, category: 'PROJECT' })).toEqual({ deadline: '2026-11-01T18:30:00.000Z', category: 'PROJECT' });
  });

  it('clears a description with an empty string', () => {
    expect(buildTaskPatch(original, { ...original, description: '   ' })).toEqual({ description: '' });
  });
});
