export type PushTokenChange =
  | { type: 'register'; token: string }
  | { type: 'disable' };

export function resolvePushTokenChange(token: string | null): PushTokenChange {
  return token ? { type: 'register', token } : { type: 'disable' };
}
