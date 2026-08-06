import { type NativeModule, requireOptionalNativeModule } from 'expo';

export type TokenEvent = { token: string | null };
export type NativeCallEvent = {
  callId: string;
  callType?: number;
  reason?: number;
  message?: string;
};
export type PendingNativeCallAction = {
  action: 'answer' | 'end';
  callId: string;
};
export type NativeCallSubscription = {
  remove: () => void;
};

type NativeCallsEvents = {
  onVoipToken: (event: TokenEvent) => void;
  onPushToken: (event: TokenEvent) => void;
  onIncomingCall: (event: NativeCallEvent) => void;
  onAnswerCall: (event: NativeCallEvent) => void;
  onEndCall: (event: NativeCallEvent) => void;
  onSetActive: (event: NativeCallEvent) => void;
  onSetInactive: (event: NativeCallEvent) => void;
  onNativeCallError: (event: NativeCallEvent) => void;
  onAudioSessionActivated: () => void;
  onAudioSessionDeactivated: () => void;
};

type NativeCallsModule = NativeModule<NativeCallsEvents> & {
  addListener<EventName extends keyof NativeCallsEvents>(
    eventName: EventName,
    listener: NativeCallsEvents[EventName],
  ): NativeCallSubscription;
  getVoipToken?: () => string | null;
  getPushTokenAsync?: () => Promise<string | null>;
  getPendingCallAction?: () => PendingNativeCallAction | null;
  reportIncomingCallAsync: (callId: string, callerName: string, hasVideo: boolean) => Promise<void>;
  reportAnswerResult: (callId: string, success: boolean) => void | Promise<void>;
  reportCallEnded: (callId: string, failed: boolean) => void | Promise<void>;
  requestEndCall: (callId: string) => void | Promise<void>;
};

const nativeModule = requireOptionalNativeModule<NativeCallsModule>('AerisNativeCalls');

function wrapSubscription(
  subscription: NativeCallSubscription | null,
): NativeCallSubscription | null {
  if (!subscription) return null;
  return {
    remove() {
      subscription.remove();
    },
  };
}

export const nativeCalls = {
  isAvailable: nativeModule !== null,

  getPushToken(): Promise<string | null> {
    if (!nativeModule) return Promise.resolve(null);
    if (nativeModule.getPushTokenAsync) return nativeModule.getPushTokenAsync();
    return Promise.resolve(nativeModule.getVoipToken?.() ?? null);
  },

  consumePendingAction(): PendingNativeCallAction | null {
    return nativeModule?.getPendingCallAction?.() ?? null;
  },

  reportIncomingCall(callId: string, callerName: string, hasVideo: boolean): Promise<void> {
    return nativeModule?.reportIncomingCallAsync(callId, callerName, hasVideo) ?? Promise.resolve();
  },

  reportAnswerResult(callId: string, success: boolean): void {
    void nativeModule?.reportAnswerResult(callId, success);
  },

  reportCallEnded(callId: string, failed = false): void {
    void nativeModule?.reportCallEnded(callId, failed);
  },

  requestEndCall(callId: string): void {
    void nativeModule?.requestEndCall(callId);
  },

  onVoipToken(listener: (event: TokenEvent) => void): NativeCallSubscription | null {
    return wrapSubscription(nativeModule?.addListener('onVoipToken', listener) ?? null);
  },

  onPushToken(listener: (event: TokenEvent) => void): NativeCallSubscription | null {
    return wrapSubscription(nativeModule?.addListener('onPushToken', listener) ?? null);
  },

  onAnswerCall(listener: (event: NativeCallEvent) => void): NativeCallSubscription | null {
    return wrapSubscription(nativeModule?.addListener('onAnswerCall', listener) ?? null);
  },

  onEndCall(listener: (event: NativeCallEvent) => void): NativeCallSubscription | null {
    return wrapSubscription(nativeModule?.addListener('onEndCall', listener) ?? null);
  },
};
