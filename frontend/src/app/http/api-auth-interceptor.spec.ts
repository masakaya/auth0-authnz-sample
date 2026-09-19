import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { AccessTokenOptions, TOKEN_SOURCE, TokenSource, TokenSourceKind } from '../auth/token-source';
import { API_ORIGIN } from '../core/api-config';
import { apiAuthInterceptor } from './api-auth-interceptor';

const API = 'http://localhost:8080';

/** Hands out the queued tokens in order and records how it was asked. */
class StubTokenSource implements TokenSource {
  readonly kind: TokenSourceKind = 'auth0';
  readonly canLogin = true;
  readonly isLoggedIn$: Observable<boolean> = of(true);
  readonly calls: AccessTokenOptions[] = [];

  constructor(private readonly tokens: (string | null)[]) {}

  getAccessToken(options?: AccessTokenOptions): Promise<string | null> {
    this.calls.push({ forceRefresh: options?.forceRefresh === true });
    return Promise.resolve(this.tokens.length > 0 ? (this.tokens.shift() ?? null) : null);
  }

  async login(): Promise<void> {}

  async logout(): Promise<void> {}
}

/** Lets the promise based interceptor run before the request reaches the testing backend. */
async function settle(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('apiAuthInterceptor', () => {
  let httpTesting: HttpTestingController;
  let http: HttpClient;
  let tokenSource: StubTokenSource;

  function setUp(tokens: (string | null)[]): void {
    tokenSource = new StubTokenSource(tokens);
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([apiAuthInterceptor])),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: API },
        { provide: TOKEN_SOURCE, useValue: tokenSource },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
  }

  afterEach(() => {
    httpTesting.verify();
  });

  it('attaches the bearer token to API requests', async () => {
    setUp(['token-1']);

    http.get(`${API}/api/private`).subscribe();
    await settle();

    const request = httpTesting.expectOne(`${API}/api/private`);
    expect(request.request.headers.get('Authorization')).toBe('Bearer token-1');
    request.flush({ endpoint: '/api/private' });
  });

  it('never attaches the token to another origin', async () => {
    setUp(['token-1']);

    http.get('https://third-party.example.test/collect').subscribe();
    await settle();

    const request = httpTesting.expectOne('https://third-party.example.test/collect');
    expect(request.request.headers.has('Authorization')).toBe(false);
    // The token source is not even consulted for foreign origins.
    expect(tokenSource.calls).toHaveLength(0);
    request.flush({});
  });

  it('sends the request as a Guest when no token is available', async () => {
    setUp([null]);

    http.get(`${API}/api/public`).subscribe();
    await settle();

    const request = httpTesting.expectOne(`${API}/api/public`);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({ endpoint: '/api/public' });
  });

  it('does not retry a Guest request that comes back 401', async () => {
    setUp([null]);

    http.get(`${API}/api/private`).subscribe({ error: () => undefined });
    await settle();

    httpTesting.expectOne(`${API}/api/private`).flush('unauthorized', { status: 401, statusText: 'Unauthorized' });
    await settle();

    httpTesting.verify();
    expect(tokenSource.calls).toEqual([{ forceRefresh: false }]);
  });

  it('refreshes once and retries when the API answers 401', async () => {
    setUp(['stale-token', 'fresh-token']);
    let status = 0;

    http.get(`${API}/api/private`, { observe: 'response' }).subscribe((response) => {
      status = response.status;
    });
    await settle();

    const first = httpTesting.expectOne(`${API}/api/private`);
    expect(first.request.headers.get('Authorization')).toBe('Bearer stale-token');
    first.flush('unauthorized', { status: 401, statusText: 'Unauthorized' });
    await settle();

    const retry = httpTesting.expectOne(`${API}/api/private`);
    expect(retry.request.headers.get('Authorization')).toBe('Bearer fresh-token');
    retry.flush({ endpoint: '/api/private' }, { status: 200, statusText: 'OK' });
    await settle();

    expect(status).toBe(200);
    expect(tokenSource.calls).toEqual([{ forceRefresh: false }, { forceRefresh: true }]);
  });

  it('retries without a token when the refresh yields nothing', async () => {
    setUp(['stale-token', null]);
    let status = 0;

    http.get(`${API}/api/public`, { observe: 'response' }).subscribe((response) => {
      status = response.status;
    });
    await settle();

    httpTesting
      .expectOne(`${API}/api/public`)
      .flush('unauthorized', { status: 401, statusText: 'Unauthorized' });
    await settle();

    const retry = httpTesting.expectOne(`${API}/api/public`);
    expect(retry.request.headers.has('Authorization')).toBe(false);
    retry.flush({ endpoint: '/api/public' }, { status: 200, statusText: 'OK' });
    await settle();

    expect(status).toBe(200);
  });

  it('stops after three attempts when even the fresh token is rejected', async () => {
    setUp(['stale-token', 'fresh-token']);
    let failure = 0;

    http.get(`${API}/api/private`).subscribe({
      error: (error: { status: number }) => {
        failure = error.status;
      },
    });
    await settle();

    const attempts: (string | null)[] = [];

    for (let index = 0; index < 3; index++) {
      const request = httpTesting.expectOne(`${API}/api/private`);
      attempts.push(request.request.headers.get('Authorization'));
      request.flush('unauthorized', { status: 401, statusText: 'Unauthorized' });
      await settle();
    }

    expect(attempts).toEqual(['Bearer stale-token', 'Bearer fresh-token', null]);
    expect(failure).toBe(401);
    // No fourth request: the Guest retry is the end of the chain.
    httpTesting.verify();
    expect(tokenSource.calls).toEqual([{ forceRefresh: false }, { forceRefresh: true }]);
  });
});
