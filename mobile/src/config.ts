// The backend the app talks to. Defaults to the live Render server; set EXPO_PUBLIC_API_URL
// (e.g. in mobile/.env.local) to use a local backend instead.
export const API_URL = (process.env.EXPO_PUBLIC_API_URL ?? 'https://justdoit-im8a.onrender.com').replace(/\/+$/, '');

// UI plan 8.1: show the "waking up" note after 4 s, give up after 90 s (Render's free tier sleeps)
export const SLOW_AFTER_MS = 4_000;
export const TIMEOUT_MS = 90_000;
