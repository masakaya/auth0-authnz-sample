import { TestBed } from '@angular/core/testing';
import { Auth0ClientService, AuthService } from '@auth0/auth0-angular';
import { of } from 'rxjs';
import { Auth0TokenSource } from './auth0-token-source';
import { NATIVE_BRIDGE_OBJECT_NAME } from './native/android-bridge-transport';
import { NativeBridgeTokenSource } from './native/native-bridge-token-source';
import { TOKEN_SOURCE } from './token-source';
import { provideTokenSource } from './token-source-providers';

/** Stands in for the SDK service so the browser branch never reaches the network. */
const authServiceStub = {
  isAuthenticated$: of(false),
  isLoading$: of(false),
  getAccessTokenSilently: () => of(undefined),
  loginWithRedirect: () => of(undefined),
  logout: () => of(undefined),
};

function injectHostBridge(): void {
  (globalThis as unknown as Record<string, unknown>)[NATIVE_BRIDGE_OBJECT_NAME] = {
    postMessage: () => undefined,
  };
}

describe('provideTokenSource', () => {
  afterEach(() => {
    delete (globalThis as unknown as Record<string, unknown>)[NATIVE_BRIDGE_OBJECT_NAME];
  });

  it('uses the native bridge when the host injected it', () => {
    injectHostBridge();
    TestBed.configureTestingModule({ providers: [provideTokenSource()] });

    expect(TestBed.inject(TOKEN_SOURCE)).toBeInstanceOf(NativeBridgeTokenSource);
  });

  it('leaves the identity provider SDK unregistered inside a WebView', () => {
    injectHostBridge();
    TestBed.configureTestingModule({ providers: [provideTokenSource()] });

    // The client token only exists when `provideAuth0` ran, so its absence is the proof.
    expect(TestBed.inject(Auth0ClientService, null, { optional: true })).toBeNull();
    // `AuthService` is declared `providedIn: 'root'`, so it is always reachable but
    // cannot be constructed without the SDK configuration.
    expect(() => TestBed.inject(AuthService)).toThrow();
  });

  it('falls back to the browser flow when no bridge is present', () => {
    TestBed.configureTestingModule({
      providers: [provideTokenSource(), { provide: AuthService, useValue: authServiceStub }],
    });

    expect(TestBed.inject(TOKEN_SOURCE)).toBeInstanceOf(Auth0TokenSource);
    // The SDK providers are registered in this branch, unlike in the WebView one.
    expect(TestBed.inject(Auth0ClientService, null, { optional: true })).not.toBeNull();
  });
});
