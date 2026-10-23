// One mapper from HTTP failures to error kinds (UI plan 4.3). Screens switch on `kind`, never on status or text.
// Kept free of React Native imports so it can be unit-tested directly.

export type ErrorKind =
  | 'VALIDATION'
  | 'BAD_CREDENTIALS'
  | 'UNAUTHORIZED'
  | 'NOT_AVAILABLE'
  | 'CONFLICT'
  | 'TASK_COMPLETED'
  | 'INVALID_TOKEN'
  | 'ONLY_VERSION'
  | 'RATE_LIMITED'
  | 'AI_UNAVAILABLE'
  | 'EMAIL_DISABLED'
  | 'FILE_TOO_LARGE'
  | 'FILE_TYPE'
  | 'STORAGE_UNAVAILABLE'
  | 'SERVER'
  | 'NETWORK'
  | 'TIMEOUT'
  | 'CANCELLED';

// Codes the backend sends in its { code, message, fieldErrors } body (see the backend's ApiException)
const SERVER_CODES: ReadonlySet<string> = new Set([
  'VALIDATION', 'BAD_CREDENTIALS', 'UNAUTHORIZED', 'NOT_AVAILABLE', 'CONFLICT', 'TASK_COMPLETED', 'INVALID_TOKEN',
  'ONLY_VERSION', 'RATE_LIMITED', 'AI_UNAVAILABLE', 'EMAIL_DISABLED', 'FILE_TOO_LARGE', 'FILE_TYPE', 'STORAGE_UNAVAILABLE',
]);

export class ApiError extends Error {
  constructor(
    readonly kind: ErrorKind,
    message: string,
    readonly status?: number,
    readonly fieldErrors: Record<string, string> = {},
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

type ErrorBody = { code?: unknown; message?: unknown; fieldErrors?: unknown };

export function toApiError(status: number, body: unknown, fallbackMessage: (kind: ErrorKind) => string): ApiError {
  const b = (typeof body === 'object' && body !== null ? body : {}) as ErrorBody;
  const code = typeof b.code === 'string' && SERVER_CODES.has(b.code) ? (b.code as ErrorKind) : undefined;
  // AI_UNAVAILABLE only ever comes from the code: a sleeping host also answers 5xx (UI plan 4.3)
  const kind = code ?? kindFromStatus(status);
  const message = typeof b.message === 'string' && b.message ? b.message : fallbackMessage(kind);
  const fields = typeof b.fieldErrors === 'object' && b.fieldErrors !== null ? (b.fieldErrors as Record<string, string>) : {};
  return new ApiError(kind, message, status, fields);
}

function kindFromStatus(status: number): ErrorKind {
  switch (status) {
    case 400:
      return 'VALIDATION';
    case 401:
      return 'UNAUTHORIZED';
    case 403:
    case 404:
      return 'NOT_AVAILABLE';
    case 409:
      return 'CONFLICT';
    case 413:
      return 'FILE_TOO_LARGE';
    case 415:
      return 'FILE_TYPE';
    case 429:
      return 'RATE_LIMITED';
    default:
      return 'SERVER';
  }
}

export function isApiError(e: unknown, kind?: ErrorKind): e is ApiError {
  return e instanceof ApiError && (kind === undefined || e.kind === kind);
}
