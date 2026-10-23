// Client-side form rules that mirror the server's (backend RegisterRequest / AuthService).
// They only make forms feel fast; the server's answer is final. No React Native imports, so it's unit-testable.
import { z } from 'zod';

import { CATEGORIES } from '../api/types';

// BCrypt reads only 72 bytes; the server rejects longer passwords, counted in UTF-8 bytes, not characters
export function utf8Bytes(text: string): number {
  return new TextEncoder().encode(text).length;
}

const password = z
  .string()
  .min(8, 'Use at least 8 characters.')
  .refine((p) => utf8Bytes(p) <= 72, 'That password is too long.');

export const signInSchema = z.object({
  username: z.string().trim().min(1, 'Enter your username.'),
  password: z.string().min(1, 'Enter your password.'),
});

export const signUpSchema = z.object({
  fullName: z.string().trim().min(1, 'Enter your name.').max(100, 'Use at most 100 characters.'),
  username: z.string().trim().regex(/^[A-Za-z0-9._]{3,50}$/, 'Use 3-50 letters, digits, dots or underscores.'),
  password,
  email: z.union([z.literal(''), z.email('Enter a valid email.').max(255, 'Use at most 255 characters.')]),
});

export const forgotSchema = z.object({
  email: z.email('Enter a valid email.'),
});

export type SignInForm = z.infer<typeof signInSchema>;
export type SignUpForm = z.infer<typeof signUpSchema>;
export type ForgotForm = z.infer<typeof forgotSchema>;

// Hours as typed: 0.1 to 999.9 with at most one decimal (server: @DecimalMin/@DecimalMax/@Digits)
export function parseHours(text: string): number | null {
  const t = text.trim();
  if (!/^\d{1,3}(\.\d)?$/.test(t)) return null;
  const n = Number(t);
  return n >= 0.1 && n <= 999.9 ? n : null;
}

// originalDeadline: when editing, an unchanged deadline may already be in the past (an overdue task can still be
// renamed). Only a NEW deadline must be in the future, which is also all the server checks (it never re-sends it).
export function taskFormSchemaFor(originalDeadline?: number) {
  return z.object({
    title: z.string().trim().min(1, 'Enter a task name.').max(200, 'Use at most 200 characters.'),
    category: z.string().refine((c) => (CATEGORIES as readonly string[]).includes(c), 'Choose a category.'),
    hours: z.string().refine((h) => parseHours(h) !== null, 'Use 0.1 to 999.9 hours, at most one decimal.'),
    deadline: z
      .date()
      .nullable()
      .refine((d) => d !== null, 'Pick a deadline.')
      .refine((d) => d === null || d.getTime() === originalDeadline || d.getTime() > Date.now(), 'Pick a time in the future.'),
    description: z.string().max(2000, 'Use at most 2000 characters.'),
  });
}

export const taskFormSchema = taskFormSchemaFor();

// What the form holds (deadline may still be empty) vs what a valid form produces
export type TaskFormInput = z.input<typeof taskFormSchema>;
export type TaskFormOutput = z.output<typeof taskFormSchema>;
