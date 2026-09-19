import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TOKEN_SOURCE } from '../auth/token-source';
import { API_ORIGIN } from '../core/api-config';
import { API_ENDPOINTS, ApiEndpoint } from '../core/api-endpoints';

interface CallResult {
  readonly pending: boolean;
  readonly status?: number;
  readonly body?: string;
}

@Component({
  selector: 'app-home',
  templateUrl: './home.html',
  styleUrl: './home.css',
})
export class Home {
  private readonly http = inject(HttpClient);
  private readonly apiOrigin = inject(API_ORIGIN);

  protected readonly tokenSource = inject(TOKEN_SOURCE);
  protected readonly endpoints = API_ENDPOINTS;
  protected readonly loggedIn = toSignal(this.tokenSource.isLoggedIn$, { initialValue: false });
  protected readonly results = signal<Record<string, CallResult>>({});

  constructor() {
    // Ask once at start-up so the native bridge can report whether the host is signed in.
    void this.tokenSource.getAccessToken();
  }

  protected login(): void {
    void this.tokenSource.login();
  }

  protected logout(): void {
    void this.tokenSource.logout();
  }

  protected call(endpoint: ApiEndpoint): void {
    this.setResult(endpoint.id, { pending: true });

    this.http.get(`${this.apiOrigin}${endpoint.path}`, { observe: 'response' }).subscribe({
      next: (response) =>
        this.setResult(endpoint.id, {
          pending: false,
          status: response.status,
          body: formatBody(response.body),
        }),
      error: (error: unknown) => {
        const response = error instanceof HttpErrorResponse ? error : null;
        this.setResult(endpoint.id, {
          pending: false,
          // Status 0 means the request never reached the API (network or CORS failure).
          status: response?.status ?? 0,
          body: response === null ? String(error) : formatBody(response.error),
        });
      },
    });
  }

  private setResult(id: string, result: CallResult): void {
    this.results.update((current) => ({ ...current, [id]: result }));
  }
}

function formatBody(body: unknown): string {
  if (body === null || body === undefined) {
    return '(empty response body)';
  }
  if (typeof body === 'string') {
    return body;
  }
  return JSON.stringify(body, null, 2);
}
