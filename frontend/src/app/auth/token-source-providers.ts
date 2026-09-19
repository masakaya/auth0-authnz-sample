import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { provideAuth0 } from '@auth0/auth0-angular';
import { environment } from '../../environments/environment';
import { Auth0TokenSource } from './auth0-token-source';
import { AndroidBridgeTransport } from './native/android-bridge-transport';
import { BRIDGE_TRANSPORT, BridgeTransport } from './native/bridge-transport';
import { NativeBridgeTokenSource } from './native/native-bridge-token-source';
import { TOKEN_SOURCE } from './token-source';

/**
 * Probes the known transports in order and returns the first one the host injected.
 * Today only Android is implemented; iOS and Windows join this list later.
 */
export function detectBridgeTransport(
  candidates: readonly BridgeTransport[] = [new AndroidBridgeTransport()],
): BridgeTransport | null {
  return candidates.find((candidate) => candidate.isAvailable()) ?? null;
}

/**
 * Picks the token source at bootstrap.
 *
 * Inside a native WebView the identity provider SDK is deliberately left unregistered:
 * silent authentication cannot work there, and the host application already holds the
 * credentials. Everywhere else the browser flow is used.
 */
export function provideTokenSource(
  transport: BridgeTransport | null = detectBridgeTransport(),
): EnvironmentProviders {
  if (transport !== null) {
    return makeEnvironmentProviders([
      { provide: BRIDGE_TRANSPORT, useValue: transport },
      { provide: TOKEN_SOURCE, useClass: NativeBridgeTokenSource },
    ]);
  }

  return makeEnvironmentProviders([
    provideAuth0({
      domain: environment.auth0.domain,
      clientId: environment.auth0.clientId,
      // Tokens are held in memory only; the refresh token keeps the session alive
      // without ever touching localStorage, sessionStorage or a cookie.
      cacheLocation: 'memory',
      useRefreshTokens: true,
      authorizationParams: {
        redirect_uri: window.location.origin,
        audience: environment.auth0.audience,
      },
    }),
    { provide: TOKEN_SOURCE, useClass: Auth0TokenSource },
  ]);
}
