import { useQueryClient } from '@tanstack/react-query';
import { createContext, ReactNode, useCallback, useContext, useEffect, useMemo, useState } from 'react';

import { setAuthToken, setUnauthorizedHandler } from '@/api/client';
import { stopReminders } from '@/notifications/reminders';
import { clearAnalysis } from '@/tasks/analyzeMemory';

import { deleteToken, readToken, saveToken } from './tokenStore';

type SignOutReason = 'expired' | 'manual';

type Session = {
  token: string | null;
  isLoading: boolean;
  // Why the user landed on sign-in, so it can say "You've been signed out…" after an expired token
  signedOutReason: SignOutReason | null;
  signIn: (token: string) => Promise<void>;
  signOut: (reason?: SignOutReason) => Promise<void>;
};

const SessionContext = createContext<Session | null>(null);

export function SessionProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setLoading] = useState(true);
  const [signedOutReason, setReason] = useState<SignOutReason | null>(null);

  const signOut = useCallback(
    async (reason: SignOutReason = 'manual') => {
      // Only a deliberate log out unregisters the phone; an expired session keeps getting reminders
      if (reason === 'manual') stopReminders();
      setAuthToken(null);
      await deleteToken();
      // Log out clears everything held in memory too (UI plan 8.3)
      queryClient.clear();
      clearAnalysis();
      setReason(reason);
      setToken(null);
    },
    [queryClient],
  );

  const signIn = useCallback(async (newToken: string) => {
    await saveToken(newToken);
    setAuthToken(newToken);
    setReason(null);
    setToken(newToken);
  }, []);

  // App start (UI plan 2.2): read the stored token; no token means the sign-in screen
  useEffect(() => {
    readToken()
      .then((stored) => {
        setAuthToken(stored);
        setToken(stored);
      })
      .finally(() => setLoading(false));
  }, []);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      void signOut('expired');
    });
  }, [signOut]);

  const value = useMemo(() => ({ token, isLoading, signedOutReason, signIn, signOut }), [token, isLoading, signedOutReason, signIn, signOut]);
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession(): Session {
  const session = useContext(SessionContext);
  if (!session) throw new Error('useSession must be used inside SessionProvider');
  return session;
}
