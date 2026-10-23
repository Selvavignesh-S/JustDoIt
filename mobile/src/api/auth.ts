// Account routes (Route plan section 2)
import { request } from './client';
import { Me, RegisterInput } from './types';

export async function login(username: string, password: string): Promise<string> {
  const res = await request<{ token: string }>('/auth/login', { method: 'POST', body: { username, password }, auth: false });
  return res.token;
}

// No token comes back: the app logs in right after (Route plan 2.1)
export function register(input: RegisterInput): Promise<Me> {
  return request<Me>('/auth/register', { method: 'POST', body: input, auth: false });
}

// Same answer whether or not the email exists. With email off the server answers EMAIL_DISABLED.
export function requestPasswordReset(email: string): Promise<{ message: string }> {
  return request('/auth/password-reset/request', { method: 'POST', body: { email }, auth: false });
}

export function getMe(): Promise<Me> {
  return request<Me>('/users/me');
}
