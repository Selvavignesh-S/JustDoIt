import { request, setAuthToken, setUnauthorizedHandler, subscribeSlow } from '../client';
import { ApiError } from '../errors';

function response(status: number, body?: unknown) {
  return { ok: status >= 200 && status < 300, status, text: async () => (body === undefined ? '' : JSON.stringify(body)) };
}

const fetchMock = jest.fn();
globalThis.fetch = fetchMock as unknown as typeof fetch;

beforeEach(() => {
  fetchMock.mockReset();
  setAuthToken(null);
  setUnauthorizedHandler(() => {});
});

describe('request', () => {
  it('sends the token on authenticated calls only', async () => {
    setAuthToken('jwt-123');
    fetchMock.mockResolvedValue(response(200, { ok: true }));

    await request('/users/me');
    await request('/auth/login', { method: 'POST', body: {}, auth: false });

    expect(fetchMock.mock.calls[0][1].headers.Authorization).toBe('Bearer jwt-123');
    expect(fetchMock.mock.calls[1][1].headers.Authorization).toBeUndefined();
  });

  it('signs out on a 401 from an authenticated call, but not from login', async () => {
    const onUnauthorized = jest.fn();
    setUnauthorizedHandler(onUnauthorized);
    fetchMock.mockResolvedValue(response(401, { code: 'UNAUTHORIZED', message: 'Sign in again to continue.' }));
    await expect(request('/users/me')).rejects.toMatchObject({ kind: 'UNAUTHORIZED' });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);

    fetchMock.mockResolvedValue(response(401, { code: 'BAD_CREDENTIALS', message: 'Username or password is incorrect.' }));
    await expect(request('/auth/login', { method: 'POST', body: {}, auth: false })).rejects.toMatchObject({ kind: 'BAD_CREDENTIALS' });
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('retries a GET once after a network error, never a write', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('Network request failed')).mockResolvedValueOnce(response(200, { n: 1 }));
    await expect(request('/tasks/summary')).resolves.toEqual({ n: 1 });
    expect(fetchMock).toHaveBeenCalledTimes(2);

    fetchMock.mockReset();
    fetchMock.mockRejectedValue(new TypeError('Network request failed'));
    await expect(request('/tasks', { method: 'POST', body: {} })).rejects.toMatchObject({ kind: 'NETWORK' });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it('turns the waking note on after 4 s and off when the request ends', async () => {
    jest.useFakeTimers();
    const seen: boolean[] = [];
    const unsubscribe = subscribeSlow((slow) => seen.push(slow));
    fetchMock.mockImplementation(() => new Promise((resolve) => setTimeout(() => resolve(response(200, {})), 6_000)));

    const pending = request('/tasks/summary');
    await jest.advanceTimersByTimeAsync(3_999);
    expect(seen).toEqual([]);
    await jest.advanceTimersByTimeAsync(1);
    expect(seen).toEqual([true]);
    await jest.advanceTimersByTimeAsync(2_000);
    await pending;
    expect(seen).toEqual([true, false]);

    unsubscribe();
    jest.useRealTimers();
  });

  it('gives up after 90 s with a TIMEOUT error', async () => {
    jest.useFakeTimers();
    fetchMock.mockImplementation(
      (_url: string, init: { signal: AbortSignal }) =>
        new Promise((_resolve, reject) => init.signal.addEventListener('abort', () => reject(new Error('aborted')))),
    );
    const pending = request('/tasks', { method: 'POST', body: {} });
    const assertion = expect(pending).rejects.toEqual(expect.any(ApiError));
    await jest.advanceTimersByTimeAsync(90_000);
    await assertion;
    await expect(pending).rejects.toMatchObject({ kind: 'TIMEOUT' });
    jest.useRealTimers();
  });
});
