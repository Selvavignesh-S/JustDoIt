// Display formatting (UI plan 4.4). Pure functions, no React Native imports, so they're unit-testable.
// Dates show in the phone's time zone; overdue / due soon / order always come from the server, never from here.

const DAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

// "Wed 23 Sep, 10:00 AM"
export function formatDateTime(value: string | Date): string {
  const d = typeof value === 'string' ? new Date(value) : value;
  const hours = d.getHours() % 12 || 12;
  const minutes = String(d.getMinutes()).padStart(2, '0');
  const meridiem = d.getHours() < 12 ? 'AM' : 'PM';
  return `${DAYS[d.getDay()]} ${d.getDate()} ${MONTHS[d.getMonth()]}, ${hours}:${minutes} ${meridiem}`;
}

// "in 4 days" / "in 5 hours" / "in 20 minutes": decoration only, for not-yet-due tasks
export function relativeFromNow(value: string | Date, now: Date = new Date()): string {
  const ms = (typeof value === 'string' ? new Date(value) : value).getTime() - now.getTime();
  const minutes = Math.max(1, Math.floor(ms / 60_000));
  if (minutes >= 60 * 24) return plural(Math.floor(minutes / (60 * 24)), 'day');
  if (minutes >= 60) return plural(Math.floor(minutes / 60), 'hour');
  return plural(minutes, 'minute');
}

function plural(n: number, unit: string) {
  return `in ${n} ${unit}${n === 1 ? '' : 's'}`;
}

// Cards and chips: "6 h", "2.5 h". Detail screen: "4 hours", "2.5 hours", "1 hour". A trailing ".0" is dropped.
export function formatHours(hours: number, long = false): string {
  const n = String(Math.round(hours * 10) / 10);
  if (!long) return `${n} h`;
  return `${n} ${n === '1' ? 'hour' : 'hours'}`;
}

// Text sent to the AI: trimmed, runs of whitespace collapsed (SRS 8.6 rule 1)
export function normalizeDescription(text: string): string {
  return text.trim().replace(/\s+/g, ' ');
}

// Default time when only a date is chosen: 11:59 PM (UI decision D20)
export function endOfDay(date: Date): Date {
  const d = new Date(date);
  d.setHours(23, 59, 0, 0);
  return d;
}
