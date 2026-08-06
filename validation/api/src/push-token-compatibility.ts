export type PushPlatform = 'ios' | 'android';
export type PushTokenKind = 'apns' | 'voip' | 'fcm';

export function isCompatiblePushToken(
  platform: PushPlatform,
  tokenKind: PushTokenKind,
): boolean {
  return platform === 'android'
    ? tokenKind === 'fcm'
    : tokenKind === 'apns' || tokenKind === 'voip';
}
