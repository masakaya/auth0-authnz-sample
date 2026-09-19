import { inject, Injectable } from '@angular/core';
import { AuthService } from '@auth0/auth0-angular';
import { catchError, defaultIfEmpty, firstValueFrom, Observable, of } from 'rxjs';
import { AccessTokenOptions, TokenSource, TokenSourceKind } from './token-source';

/**
 * Browser token source: redirects to Universal Login (Authorization Code + PKCE) and
 * keeps tokens in memory only. Used whenever the page is not running inside a native
 * WebView that provides the bridge.
 */
@Injectable()
export class Auth0TokenSource implements TokenSource {
  private readonly auth = inject(AuthService);

  readonly kind: TokenSourceKind = 'auth0';
  readonly canLogin = true;

  /** `isAuthenticated$` already waits for the SDK to finish loading, so no extra gating. */
  readonly isLoggedIn$: Observable<boolean> = this.auth.isAuthenticated$;

  async getAccessToken(options?: AccessTokenOptions): Promise<string | null> {
    const authenticated = await firstValueFrom(this.auth.isAuthenticated$.pipe(defaultIfEmpty(false)));
    if (!authenticated) {
      // Browsing as Guest is a supported state, so never start a login from here.
      return null;
    }

    const token = await firstValueFrom(
      this.auth
        .getAccessTokenSilently({ cacheMode: options?.forceRefresh ? 'off' : 'on' })
        .pipe(
          catchError((error: unknown) => {
            console.warn('[auth0] silent token request failed', error);
            return of(undefined);
          }),
          defaultIfEmpty(undefined),
        ),
    );

    return token ?? null;
  }

  async login(): Promise<void> {
    await firstValueFrom(this.auth.loginWithRedirect().pipe(defaultIfEmpty(undefined)));
  }

  async logout(): Promise<void> {
    await firstValueFrom(
      this.auth
        .logout({ logoutParams: { returnTo: window.location.origin } })
        .pipe(defaultIfEmpty(undefined)),
    );
  }
}
