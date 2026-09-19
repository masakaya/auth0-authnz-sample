import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { BRIDGE_ERROR_CODES, BridgeErrorCode, GetTokenMessage } from './bridge-messages';
import { BRIDGE_TRANSPORT, BridgeTransport } from './bridge-transport';
import {
  BRIDGE_REQUEST_TIMEOUT_MS,
  NativeBridgeTokenSource,
  TOKEN_EXPIRY_SKEW_MS,
} from './native-bridge-token-source';

class FakeTransport implements BridgeTransport {
  readonly name = 'fake';
  readonly sent: string[] = [];
  available = true;

  private onMessage: ((raw: unknown) => void) | null = null;

  isAvailable(): boolean {
    return this.available;
  }

  connect(onMessage: (raw: unknown) => void): void {
    this.onMessage = onMessage;
  }

  disconnect(): void {
    this.onMessage = null;
  }

  postMessage(raw: string): void {
    this.sent.push(raw);
  }

  /** Simulates a native to web payload. */
  emit(raw: unknown): void {
    this.onMessage?.(raw);
  }

  requests(): GetTokenMessage[] {
    return this.sent.map((raw) => JSON.parse(raw) as GetTokenMessage);
  }

  lastRequest(): GetTokenMessage {
    const requests = this.requests();
    return requests[requests.length - 1];
  }
}

function tokenResult(requestId: string, accessToken: string, expiresAt: number): string {
  return JSON.stringify({ version: 1, type: 'tokenResult', requestId, accessToken, expiresAt });
}

function errorResult(requestId: string, code: BridgeErrorCode): string {
  return JSON.stringify({ version: 1, type: 'error', requestId, code, message: 'diagnostic only' });
}

describe('NativeBridgeTokenSource', () => {
  let transport: FakeTransport;
  let source: NativeBridgeTokenSource;

  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-01-01T00:00:00Z'));
    transport = new FakeTransport();
    TestBed.configureTestingModule({
      providers: [{ provide: BRIDGE_TRANSPORT, useValue: transport }, NativeBridgeTokenSource],
    });
    source = TestBed.inject(NativeBridgeTokenSource);
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  /** Runs the first request to completion and returns the token that was handed out. */
  async function primeToken(accessToken: string, lifetimeMs: number): Promise<void> {
    const pending = source.getAccessToken();
    transport.emit(tokenResult(transport.lastRequest().requestId, accessToken, Date.now() + lifetimeMs));
    await expect(pending).resolves.toBe(accessToken);
  }

  it('sends a getToken request and resolves with the token the host returns', async () => {
    const pending = source.getAccessToken();

    expect(transport.sent).toHaveLength(1);
    expect(transport.lastRequest()).toMatchObject({
      version: 1,
      type: 'getToken',
      forceRefresh: false,
    });
    expect(transport.lastRequest().requestId).toBeTruthy();

    transport.emit(tokenResult(transport.lastRequest().requestId, 'token-1', Date.now() + 300_000));

    await expect(pending).resolves.toBe('token-1');
    await expect(firstValueFrom(source.isLoggedIn$)).resolves.toBe(true);
  });

  it('collapses concurrent calls into a single getToken request', async () => {
    const pending = [source.getAccessToken(), source.getAccessToken(), source.getAccessToken()];

    expect(transport.sent).toHaveLength(1);

    transport.emit(tokenResult(transport.lastRequest().requestId, 'token-1', Date.now() + 300_000));

    await expect(Promise.all(pending)).resolves.toEqual(['token-1', 'token-1', 'token-1']);
    expect(transport.sent).toHaveLength(1);
  });

  it('reuses the token until the expiry margin and asks again afterwards', async () => {
    await primeToken('token-1', 120_000);

    await expect(source.getAccessToken()).resolves.toBe('token-1');
    expect(transport.sent).toHaveLength(1);

    // One millisecond before the margin the token is still reused.
    vi.setSystemTime(Date.now() + 120_000 - TOKEN_EXPIRY_SKEW_MS - 1);
    await expect(source.getAccessToken()).resolves.toBe('token-1');
    expect(transport.sent).toHaveLength(1);

    // At the margin a new request goes out.
    vi.setSystemTime(Date.now() + 1);
    const refreshed = source.getAccessToken();
    expect(transport.sent).toHaveLength(2);

    transport.emit(tokenResult(transport.lastRequest().requestId, 'token-2', Date.now() + 300_000));
    await expect(refreshed).resolves.toBe('token-2');
  });

  it('bypasses the cache when forceRefresh is requested', async () => {
    await primeToken('token-1', 300_000);

    const forced = source.getAccessToken({ forceRefresh: true });

    expect(transport.sent).toHaveLength(2);
    expect(transport.lastRequest().forceRefresh).toBe(true);

    transport.emit(tokenResult(transport.lastRequest().requestId, 'token-2', Date.now() + 300_000));
    await expect(forced).resolves.toBe('token-2');

    // The refreshed token replaces the cached one.
    await expect(source.getAccessToken()).resolves.toBe('token-2');
    expect(transport.sent).toHaveLength(2);
  });

  it('collapses concurrent forceRefresh calls into one request', async () => {
    const pending = [
      source.getAccessToken({ forceRefresh: true }),
      source.getAccessToken({ forceRefresh: true }),
    ];

    expect(transport.sent).toHaveLength(1);

    transport.emit(tokenResult(transport.lastRequest().requestId, 'token-1', Date.now() + 300_000));
    await expect(Promise.all(pending)).resolves.toEqual(['token-1', 'token-1']);
  });

  it.each([...BRIDGE_ERROR_CODES])('continues without a token when the host answers %s', async (code) => {
    const pending = source.getAccessToken();

    transport.emit(errorResult(transport.lastRequest().requestId, code));

    await expect(pending).resolves.toBeNull();
    await expect(firstValueFrom(source.isLoggedIn$)).resolves.toBe(false);
  });

  it('gives up and continues without a token when the host never answers', async () => {
    const pending = source.getAccessToken();

    vi.advanceTimersByTime(BRIDGE_REQUEST_TIMEOUT_MS);

    await expect(pending).resolves.toBeNull();
  });

  it('resolves without a token when the bridge is not available', async () => {
    transport.available = false;

    await expect(source.getAccessToken()).resolves.toBeNull();
    expect(transport.sent).toHaveLength(0);
  });

  it('discards the held token when the host reports a logout', async () => {
    await primeToken('token-1', 300_000);

    transport.emit(JSON.stringify({ version: 1, type: 'logout' }));

    await expect(firstValueFrom(source.isLoggedIn$)).resolves.toBe(false);

    // The cached token is gone, so the next call has to ask the host again.
    source.getAccessToken();
    expect(transport.sent).toHaveLength(2);
  });

  it('answers requests that are still in flight when a logout arrives', async () => {
    const pending = source.getAccessToken();

    transport.emit(JSON.stringify({ version: 1, type: 'logout' }));

    await expect(pending).resolves.toBeNull();
  });

  it('ignores malformed, unknown and unrelated messages', async () => {
    let settled = false;
    const pending = source.getAccessToken().then((token) => {
      settled = true;
      return token;
    });
    const requestId = transport.lastRequest().requestId;
    const expiresAt = Date.now() + 300_000;

    transport.emit('{ this is not json');
    transport.emit({ version: 1, type: 'tokenResult', requestId, accessToken: 'x', expiresAt });
    transport.emit(JSON.stringify({ version: 2, type: 'tokenResult', requestId, accessToken: 'x', expiresAt }));
    transport.emit(JSON.stringify({ version: 1, type: 'tokenResult', requestId: 'someone-else', accessToken: 'x', expiresAt }));
    transport.emit(JSON.stringify({ version: 1, type: 'unknownType', requestId }));
    transport.emit(JSON.stringify({ version: 1, type: 'tokenResult', requestId }));

    await Promise.resolve();
    await Promise.resolve();
    expect(settled).toBe(false);

    // A well formed answer still completes the very same request.
    transport.emit(tokenResult(requestId, 'token-1', expiresAt));
    await expect(pending).resolves.toBe('token-1');
  });

  it('does not offer the sign-in interaction', () => {
    expect(source.canLogin).toBe(false);
    expect(source.kind).toBe('native-bridge');
  });
});
