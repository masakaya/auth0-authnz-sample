import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

/** Options accepted by {@link TokenSource.getAccessToken}. */
export interface AccessTokenOptions {
  /** Ignore any cached token and ask the underlying source for a fresh one. */
  readonly forceRefresh?: boolean;
}

/** Identifies the implementation currently serving tokens (used for display and tests). */
export type TokenSourceKind = 'auth0' | 'native-bridge';

/**
 * Where the application gets its access tokens from.
 *
 * Two implementations exist: the browser one talks to the identity provider directly,
 * the WebView one asks the surrounding native application over a message bridge.
 * Everything above this interface (interceptor, screens) is unaware of the difference.
 */
export interface TokenSource {
  readonly kind: TokenSourceKind;

  /**
   * Whether this source owns the sign-in interaction. The native bridge does not:
   * the host application signs the user in, so the UI must not offer login or logout.
   */
  readonly canLogin: boolean;

  /** Emits whether a usable access token is currently available. */
  readonly isLoggedIn$: Observable<boolean>;

  /**
   * Resolves with an access token, or `null` when none can be obtained.
   * Implementations never reject: "no token" simply means "continue as Guest".
   */
  getAccessToken(options?: AccessTokenOptions): Promise<string | null>;

  login(): Promise<void>;

  logout(): Promise<void>;
}

export const TOKEN_SOURCE = new InjectionToken<TokenSource>('TOKEN_SOURCE');
