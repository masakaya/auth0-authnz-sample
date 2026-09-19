import { inject, Injectable, OnDestroy } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { AccessTokenOptions, TokenSource, TokenSourceKind } from '../token-source';
import { BRIDGE_PROTOCOL_VERSION, GetTokenMessage, parseNativeMessage } from './bridge-messages';
import { BRIDGE_TRANSPORT } from './bridge-transport';

/** A cached token is reused until this margin before `expiresAt`. */
export const TOKEN_EXPIRY_SKEW_MS = 30_000;

/**
 * Timeout for a single `getToken` request. The specification allows a flat 60 seconds so
 * that a biometric prompt on the native side does not trip the timer.
 */
export const BRIDGE_REQUEST_TIMEOUT_MS = 60_000;

interface CachedToken {
  readonly accessToken: string;
  readonly expiresAt: number;
}

interface PendingRequest {
  readonly settle: (token: string | null) => void;
  readonly timer: ReturnType<typeof setTimeout>;
}

/**
 * WebView token source: asks the surrounding native application for an access token.
 *
 * The bridge only ever carries access tokens. Refresh tokens, ID tokens and the user
 * profile stay on the native side, and the token this class holds lives in memory only.
 */
@Injectable()
export class NativeBridgeTokenSource implements TokenSource, OnDestroy {
  private readonly transport = inject(BRIDGE_TRANSPORT);
  private readonly loggedIn = new BehaviorSubject<boolean>(false);
  private readonly pending = new Map<string, PendingRequest>();

  private cached: CachedToken | null = null;
  private inFlight: Promise<string | null> | null = null;
  private inFlightForced: Promise<string | null> | null = null;

  readonly kind: TokenSourceKind = 'native-bridge';

  /** The host application owns the sign-in interaction, so the web layer must not offer it. */
  readonly canLogin = false;

  /** Reflects whether a usable access token is currently held. */
  readonly isLoggedIn$: Observable<boolean> = this.loggedIn.asObservable();

  constructor() {
    this.transport.connect((raw) => this.handleMessage(raw));
  }

  getAccessToken(options?: AccessTokenOptions): Promise<string | null> {
    if (options?.forceRefresh) {
      // Forced requests join each other but never a plain one, so a refresh really refreshes.
      this.inFlightForced ??= this.send(true).finally(() => {
        this.inFlightForced = null;
      });
      return this.inFlightForced;
    }

    const cached = this.reusableToken();
    if (cached !== null) {
      return Promise.resolve(cached);
    }

    // Single flight: concurrent callers share one request. Sending several in parallel can
    // trip refresh token reuse detection on the native side and kill the whole token family.
    this.inFlight ??= this.send(false).finally(() => {
      this.inFlight = null;
    });
    return this.inFlight;
  }

  /** The host application performs the real sign-in; nothing to do here. */
  async login(): Promise<void> {
    console.warn('[native-bridge] login is driven by the host application and was ignored');
  }

  /** The host application performs the real sign-out; drop whatever is held locally. */
  async logout(): Promise<void> {
    console.warn('[native-bridge] logout is driven by the host application; discarding the local token');
    this.discardEverything();
  }

  ngOnDestroy(): void {
    this.transport.disconnect();
    this.discardEverything();
  }

  private reusableToken(): string | null {
    if (this.cached === null) {
      return null;
    }
    if (Date.now() >= this.cached.expiresAt - TOKEN_EXPIRY_SKEW_MS) {
      this.cached = null;
      return null;
    }
    return this.cached.accessToken;
  }

  private send(forceRefresh: boolean): Promise<string | null> {
    if (!this.transport.isAvailable()) {
      return Promise.resolve(null);
    }

    const requestId = newRequestId();
    const message: GetTokenMessage = {
      version: BRIDGE_PROTOCOL_VERSION,
      type: 'getToken',
      requestId,
      forceRefresh,
    };

    return new Promise<string | null>((resolve) => {
      const timer = setTimeout(() => {
        // A timeout is treated like `internal_error`: continue as Guest.
        this.pending.delete(requestId);
        console.warn(`[native-bridge] getToken timed out after ${BRIDGE_REQUEST_TIMEOUT_MS} ms`);
        resolve(null);
      }, BRIDGE_REQUEST_TIMEOUT_MS);

      this.pending.set(requestId, { settle: resolve, timer });

      try {
        this.transport.postMessage(JSON.stringify(message));
      } catch (error) {
        console.warn('[native-bridge] failed to post getToken', error);
        this.resolveRequest(requestId, null);
      }
    });
  }

  private handleMessage(raw: unknown): void {
    // Malformed JSON, unknown versions and unknown types are ignored without throwing.
    const message = parseNativeMessage(raw);
    if (message === null) {
      return;
    }

    switch (message.type) {
      case 'tokenResult':
        // Answers to requests we do not know about (for example from a previous page) are ignored.
        if (!this.pending.has(message.requestId)) {
          return;
        }
        this.cached = { accessToken: message.accessToken, expiresAt: message.expiresAt };
        this.loggedIn.next(true);
        this.resolveRequest(message.requestId, message.accessToken);
        return;

      case 'error':
        if (!this.pending.has(message.requestId)) {
          return;
        }
        if (message.message !== undefined) {
          console.warn(`[native-bridge] ${message.code}: ${message.message}`);
        }
        // Every error code means the same thing to this layer: continue without a token.
        this.cached = null;
        this.loggedIn.next(false);
        this.resolveRequest(message.requestId, null);
        return;

      case 'logout':
        this.discardEverything();
        return;
    }
  }

  private resolveRequest(requestId: string, token: string | null): void {
    const request = this.pending.get(requestId);
    if (request === undefined) {
      return;
    }
    this.pending.delete(requestId);
    clearTimeout(request.timer);
    request.settle(token);
  }

  /** Drops the held token and answers every request still in flight with "no token". */
  private discardEverything(): void {
    this.cached = null;
    for (const requestId of [...this.pending.keys()]) {
      this.resolveRequest(requestId, null);
    }
    this.loggedIn.next(false);
  }
}

function newRequestId(): string {
  const cryptoApi: Crypto | undefined = globalThis.crypto;
  if (cryptoApi !== undefined && typeof cryptoApi.randomUUID === 'function') {
    return cryptoApi.randomUUID();
  }
  return `${Date.now().toString(16)}-${Math.random().toString(16).slice(2)}`;
}
