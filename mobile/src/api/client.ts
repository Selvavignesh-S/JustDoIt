// The one fetch wrapper (UI plan 8.1). Every request goes through here.
import { API_URL, SLOW_AFTER_MS, TIMEOUT_MS } from '@/config';
import { strings } from '@/strings/en';

import { ApiError, ErrorKind, toApiError } from './errors';

type Method = 'GET' | 'POST' | 'PATCH' | 'DELETE';

export type RequestOptions = {
  method?: Method;
  body?: unknown;
  // false for register, login and password reset: no token is sent and a 401 doesn't sign anyone out
  auth?: boolean;
  // lets a screen cancel a long wait (e.g. the AI analyze "Cancel")
  signal?: AbortSignal;
};

let authToken: string | null = null;
let onUnauthorized: () => void = () => {};

export function setAuthToken(token: string | null) {
  authToken = token;
}

// Any 401 on an authenticated call clears the session and returns to sign-in (UI plan 2.2)
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

// "Waking up the server" note: true while any request has been running for more than 4 s
let slowCount = 0;
const slowListeners = new Set<(slow: boolean) => void>();
function changeSlow(delta: number) {
  const wasSlow = slowCount > 0;
  slowCount += delta;
  if (wasSlow !== slowCount > 0) slowListeners.forEach((l) => l(slowCount > 0));
}
export function subscribeSlow(listener: (slow: boolean) => void) {
  slowListeners.add(listener);
  return () => {
    slowListeners.delete(listener);
  };
}

const message = (kind: ErrorKind) => (strings.errors as Record<string, string>)[kind] ?? strings.errors.SERVER;

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  try {
    return await attempt<T>(path, method, options);
  } catch (e) {
    // GET only, once, network errors only: never repeat a write (a repeated create would duplicate a task)
    if (method === 'GET' && e instanceof ApiError && e.kind === 'NETWORK') {
      return attempt<T>(path, method, options);
    }
    throw e;
  }
}

async function attempt<T>(path: string, method: Method, { body, auth = true, signal }: RequestOptions): Promise<T> {
  const controller = new AbortController();
  let timedOut = false;
  let markedSlow = false;
  const timeout = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, TIMEOUT_MS);
  const slow = setTimeout(() => {
    markedSlow = true;
    changeSlow(+1);
  }, SLOW_AFTER_MS);
  const cancel = () => controller.abort();
  signal?.addEventListener('abort', cancel);

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (auth && authToken) headers.Authorization = `Bearer ${authToken}`;

  try {
    const res = await fetch(API_URL + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: controller.signal,
    });
    const text = await res.text();
    const data = parseJson(text);
    if (!res.ok) {
      const err = toApiError(res.status, data, message);
      if (auth && err.kind === 'UNAUTHORIZED') onUnauthorized();
      throw err;
    }
    return data as T;
  } catch (e) {
    if (e instanceof ApiError) throw e;
    if (timedOut) throw new ApiError('TIMEOUT', message('TIMEOUT'));
    if (signal?.aborted) throw new ApiError('CANCELLED', 'Cancelled');
    throw new ApiError('NETWORK', message('NETWORK'));
  } finally {
    clearTimeout(timeout);
    clearTimeout(slow);
    signal?.removeEventListener('abort', cancel);
    if (markedSlow) changeSlow(-1);
  }
}

function parseJson(text: string): unknown {
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    return undefined;
  }
}
