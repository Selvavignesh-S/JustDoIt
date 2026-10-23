import { endOfDay, formatDateTime, formatHours, normalizeDescription, relativeFromNow } from '../format';

describe('formatting', () => {
  it('formats a deadline like "Wed 23 Sep, 10:00 AM" in local time', () => {
    expect(formatDateTime(new Date(2026, 8, 23, 10, 0))).toBe('Wed 23 Sep, 10:00 AM');
    expect(formatDateTime(new Date(2026, 8, 23, 23, 59))).toBe('Wed 23 Sep, 11:59 PM');
    expect(formatDateTime(new Date(2026, 8, 23, 0, 5))).toBe('Wed 23 Sep, 12:05 AM');
    expect(formatDateTime(new Date(2026, 8, 23, 12, 30))).toBe('Wed 23 Sep, 12:30 PM');
  });

  it('describes time left, rounded down', () => {
    const now = new Date(2026, 9, 24, 10, 0);
    expect(relativeFromNow(new Date(2026, 9, 28, 11, 0), now)).toBe('in 4 days');
    expect(relativeFromNow(new Date(2026, 9, 25, 9, 59), now)).toBe('in 23 hours');
    expect(relativeFromNow(new Date(2026, 9, 24, 11, 0), now)).toBe('in 1 hour');
    expect(relativeFromNow(new Date(2026, 9, 24, 10, 20), now)).toBe('in 20 minutes');
  });

  it('drops a trailing .0 from hours', () => {
    expect(formatHours(6)).toBe('6 h');
    expect(formatHours(2.5)).toBe('2.5 h');
    expect(formatHours(4, true)).toBe('4 hours');
    expect(formatHours(1, true)).toBe('1 hour');
  });

  it('normalizes the AI description and defaults the time to 11:59 PM', () => {
    expect(normalizeDescription('  finish   lab\n report ')).toBe('finish lab report');
    const d = endOfDay(new Date(2026, 9, 24, 8, 15));
    expect([d.getHours(), d.getMinutes()]).toEqual([23, 59]);
  });
});
