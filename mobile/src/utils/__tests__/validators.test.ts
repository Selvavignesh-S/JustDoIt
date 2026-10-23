import { parseHours, signUpSchema, taskFormSchema, utf8Bytes } from '../validators';

const valid = { fullName: 'Selva', username: 'selva_v', password: 'Password123', email: '' };

function errorsFor(input: Partial<typeof valid>) {
  const result = signUpSchema.safeParse({ ...valid, ...input });
  return result.success ? {} : Object.fromEntries(result.error.issues.map((i) => [i.path[0], i.message]));
}

describe('sign-up rules mirror the server', () => {
  it('accepts a valid form, email optional', () => {
    expect(errorsFor({})).toEqual({});
    expect(errorsFor({ email: 'me@college.edu' })).toEqual({});
  });

  it('username: 3-50 letters, digits, dots or underscores', () => {
    expect(errorsFor({ username: 'ab' }).username).toBeDefined();
    expect(errorsFor({ username: 'has space' }).username).toBeDefined();
    expect(errorsFor({ username: 'Selva.V_2' }).username).toBeUndefined();
  });

  it('password: at least 8 characters and at most 72 UTF-8 bytes', () => {
    expect(errorsFor({ password: 'short' }).password).toBe('Use at least 8 characters.');
    expect(utf8Bytes('😀')).toBe(4);
    // 19 emoji = 19 characters but 76 bytes: too long for BCrypt, like the server's test
    expect(errorsFor({ password: '😀'.repeat(19) }).password).toBe('That password is too long.');
  });

  it('email must look like an email when given', () => {
    expect(errorsFor({ email: 'not-an-email' }).email).toBe('Enter a valid email.');
  });
});

describe('task form rules mirror the server', () => {
  const future = new Date(Date.now() + 86_400_000);
  const form = { title: 'Lab report', category: 'LAB', hours: '2.5', deadline: future, description: '' };
  const errorsOf = (input: object) => {
    const r = taskFormSchema.safeParse({ ...form, ...input });
    return r.success ? {} : Object.fromEntries(r.error.issues.map((i) => [i.path[0], i.message]));
  };

  it('hours: 0.1 to 999.9, at most one decimal', () => {
    expect(parseHours('2.5')).toBe(2.5);
    expect(parseHours(' 6 ')).toBe(6);
    expect(parseHours('0.1')).toBe(0.1);
    expect(parseHours('0')).toBeNull();
    expect(parseHours('2.55')).toBeNull();
    expect(parseHours('1000')).toBeNull();
    expect(parseHours('abc')).toBeNull();
  });

  it('needs a title, a real category, and a future deadline', () => {
    expect(errorsOf({})).toEqual({});
    expect(errorsOf({ title: '  ' }).title).toBe('Enter a task name.');
    expect(errorsOf({ category: '' }).category).toBe('Choose a category.');
    expect(errorsOf({ category: 'STUDY' }).category).toBe('Choose a category.');
    expect(errorsOf({ deadline: null }).deadline).toBe('Pick a deadline.');
    expect(errorsOf({ deadline: new Date(Date.now() - 60_000) }).deadline).toBe('Pick a time in the future.');
  });
});
