import { toApiError } from '../errors';

const fallback = (kind: string) => `fallback ${kind}`;

describe('toApiError', () => {
  it('uses the server code and message when the code is known', () => {
    const e = toApiError(401, { code: 'BAD_CREDENTIALS', message: 'Username or password is incorrect.' }, fallback);
    expect(e.kind).toBe('BAD_CREDENTIALS');
    expect(e.message).toBe('Username or password is incorrect.');
  });

  it('keeps field errors for forms', () => {
    const e = toApiError(400, { code: 'VALIDATION', message: 'x', fieldErrors: { username: 'That username is taken.' } }, fallback);
    expect(e.fieldErrors).toEqual({ username: 'That username is taken.' });
  });

  it('falls back to the status when there is no known code', () => {
    expect(toApiError(404, undefined, fallback).kind).toBe('NOT_AVAILABLE');
    expect(toApiError(409, {}, fallback).kind).toBe('CONFLICT');
    expect(toApiError(429, 'not json', fallback).kind).toBe('RATE_LIMITED');
    expect(toApiError(502, undefined, fallback)).toMatchObject({ kind: 'SERVER', message: 'fallback SERVER' });
  });

  it('never treats a bare 503 as AI_UNAVAILABLE: a waking server also answers 5xx', () => {
    expect(toApiError(503, undefined, fallback).kind).toBe('SERVER');
    expect(toApiError(503, { code: 'AI_UNAVAILABLE', message: 'm' }, fallback).kind).toBe('AI_UNAVAILABLE');
  });

  it('ignores unknown codes', () => {
    expect(toApiError(400, { code: 'SOMETHING_NEW' }, fallback).kind).toBe('VALIDATION');
  });
});
